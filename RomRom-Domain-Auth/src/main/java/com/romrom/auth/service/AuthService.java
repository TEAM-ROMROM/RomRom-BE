package com.romrom.auth.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static com.romrom.common.util.CommonUtil.nvl;

import com.google.firebase.auth.FirebaseToken;
import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.LoginRequest;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.EmailAlreadyRegisteredException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.SuspendedMemberException;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.member.service.LoginHistoryService;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhnicknamegenerator.core.SuhRandomKit;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder().locale("ko").uuidLength(4).numberLength(4).build();

  private final MemberRepository memberRepository;
  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final FirebaseTokenVerifier firebaseTokenVerifier;
  private final LoginHistoryService loginHistoryService;

  /**
   * Firebase 통합 로그인: 토큰 검증 > 회원 조회·생성 > 플랫폼 검사 > 정지 확인(만료 시 자동 해제) > JWT 발급
   * 회원 저장·정지 해제는 한 트랜잭션 (부분 반영 방지)
   *
   * @param request firebaseIdToken, providerId, profile, client
   */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    FirebaseToken firebaseToken = verifyFirebaseToken(request);

    String email = resolveLoginEmail(firebaseToken, request);
    SocialPlatform socialPlatform = mapProviderIdToSocialPlatform(request.getProviderId());
    log.debug("Firebase 로그인 시도: email={}, providerId={}, platform={}", email, request.getProviderId(), socialPlatform);

    // 카카오만 firebaseUid 사용 (이메일 미동의 회원 식별)
    String kakaoFirebaseUid = socialPlatform == SocialPlatform.KAKAO ? firebaseToken.getUid() : null;
    Member member = findExistingMember(socialPlatform, email, kakaoFirebaseUid)
        .map(existingMember -> prepareExistingMemberLogin(existingMember, socialPlatform, kakaoFirebaseUid))
        .orElseGet(() -> buildNewMember(email, kakaoFirebaseUid, socialPlatform, request));
    memberRepository.save(member);

    // 정지 계정: 기간 만료면 자동 해제, 아니면 토큰 없이 정지 정보 반환
    if (member.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      if (!isSuspensionExpired(member)) {
        return buildSuspendedLoginResponse(member, socialPlatform);
      }
      liftExpiredSuspension(member);
    }

    return issueLoginTokens(member, socialPlatform);
  }

  /**
   * Firebase ID Token 검증 (실패 시 memberId 미상이라 로그인 이력 기록 안 함)
   */
  private FirebaseToken verifyFirebaseToken(LoginRequest request) {
    try {
      return firebaseTokenVerifier.verify(request.getFirebaseIdToken());
    } catch (RuntimeException firebaseVerifyFailure) {
      log.warn("LoginHistory 기록 skip: Firebase 토큰 검증 실패로 memberId 미상. error={}", firebaseVerifyFailure.getMessage());
      throw firebaseVerifyFailure;
    }
  }

  /**
   * 로그인 email 결정: Firebase 토큰 > request.email > profile.email (카카오 Custom Token은 토큰에 email 없음)
   */
  private String resolveLoginEmail(FirebaseToken firebaseToken, LoginRequest request) {
    if (firebaseToken.getEmail() != null) {
      return firebaseToken.getEmail();
    }
    if (request.getEmail() != null) {
      return request.getEmail();
    }
    return request.getProfile() != null ? request.getProfile().getEmail() : null;
  }

  /**
   * 기존 회원 조회: email 우선, 카카오는 email 없거나 미조회 시 firebaseUid로 재조회
   */
  private Optional<Member> findExistingMember(SocialPlatform socialPlatform, String email, String kakaoFirebaseUid) {
    if (socialPlatform != SocialPlatform.KAKAO) {
      return memberRepository.findByEmail(email);
    }
    Optional<Member> memberFoundByEmail = (email != null) ? memberRepository.findByEmail(email) : Optional.empty();
    return memberFoundByEmail.isPresent() ? memberFoundByEmail : memberRepository.findByFirebaseUid(kakaoFirebaseUid);
  }

  /**
   * 기존 회원 로그인 준비: 소셜 플랫폼 일치 검사 > (카카오) firebaseUid 최초 세팅 > 첫 로그인 해제
   */
  private Member prepareExistingMemberLogin(Member existingMember, SocialPlatform socialPlatform, String kakaoFirebaseUid) {
    if (existingMember.getSocialPlatform() != socialPlatform) {
      // 360 뷰: 소셜 플랫폼 불일치로 로그인 실패 기록
      loginHistoryService.recordFromCurrentRequest(
          existingMember.getMemberId(), socialPlatform, LoginResult.FAIL,
          "EmailAlreadyRegistered: existing=" + existingMember.getSocialPlatform());
      throw new EmailAlreadyRegisteredException(existingMember.getSocialPlatform());
    }
    // oidc.kakao > custom token 전환 회원
    if (kakaoFirebaseUid != null && existingMember.getFirebaseUid() == null) {
      existingMember.setFirebaseUid(kakaoFirebaseUid);
    }
    existingMember.setIsFirstLogin(false);
    return existingMember;
  }

  /**
   * 신규 회원 생성 (firebaseUid는 카카오만, 그 외 null)
   */
  private Member buildNewMember(String email, String kakaoFirebaseUid, SocialPlatform socialPlatform,
      LoginRequest request) {
    return Member.builder()
        .email(email)
        .firebaseUid(kakaoFirebaseUid)
        .nickname(suhRandomKit.nicknameWithNumber())
        .socialPlatform(socialPlatform)
        .profileUrl(request.getProfile() != null ? request.getProfile().getPhotoUrl() : null)
        .role(Role.ROLE_USER)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .isFirstLogin(true)
        .isFirstItemPosted(false)
        .isItemCategorySaved(false)
        .isMemberLocationSaved(false)
        .isRequiredTermsAgreed(false)
        .isMarketingInfoAgreed(false)
        .isActivityNotificationAgreed(false)
        .isChatNotificationAgreed(false)
        .isContentNotificationAgreed(false)
        .isTradeNotificationAgreed(false)
        .isDeleted(false)
        .totalLikeCount(0)
        .build();
  }

  private boolean isSuspensionExpired(Member member) {
    return member.getSuspendedUntil() != null && member.getSuspendedUntil().isBefore(LocalDateTime.now());
  }

  /**
   * 정지 기간 만료 자동 해제: 회원 상태 복구 > 진행 중 제재 이력 해제 기록
   */
  private void liftExpiredSuspension(Member member) {
    log.info("정지 기간 만료, 자동 해제: memberId={}", member.getMemberId());
    member.setAccountStatus(AccountStatus.ACTIVE_ACCOUNT);
    member.setSuspendReason(null);
    member.setSuspendedAt(null);
    member.setSuspendedUntil(null);
    memberRepository.save(member);

    sanctionHistoryRepository
        .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(member.getMemberId())
        .ifPresent(sanctionToAutoLift -> {
          sanctionToAutoLift.setLiftedAt(LocalDateTime.now());
          sanctionToAutoLift.setLiftedReason("자동 해제 (기간 만료)");
          sanctionHistoryRepository.save(sanctionToAutoLift);
          log.debug("제재 이력 자동 해제: sanctionHistoryId={}", sanctionToAutoLift.getSanctionHistoryId());
        });
  }

  /**
   * 정지 중 로그인 응답: 토큰 없이 정지 사유·기한만 (실패 이력 기록)
   */
  private AuthResponse buildSuspendedLoginResponse(Member member, SocialPlatform socialPlatform) {
    log.info("정지 계정 로그인 시도: memberId={}, suspendedUntil={}", member.getMemberId(), member.getSuspendedUntil());
    loginHistoryService.recordFromCurrentRequest(
        member.getMemberId(), socialPlatform, LoginResult.FAIL,
        "SuspendedAccount: " + member.getSuspendReason());
    return AuthResponse.builder()
        .accessToken(null)
        .refreshToken(null)
        .accountStatus(member.getAccountStatus())
        .suspendReason(member.getSuspendReason())
        .suspendedUntil(member.getSuspendedUntil())
        .build();
  }

  /**
   * 토큰 발급: access·refresh 생성 > refresh Redis 저장(RT:{memberId}) > 성공 이력 기록
   */
  private AuthResponse issueLoginTokens(Member member, SocialPlatform socialPlatform) {
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);
    log.debug("로그인 성공: memberId={}", customUserDetails.getMemberId());

    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    loginHistoryService.recordFromCurrentRequest(
        member.getMemberId(), socialPlatform, LoginResult.SUCCESS, null);

    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .accountStatus(member.getAccountStatus())
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .isItemCategorySaved(member.getIsItemCategorySaved())
        .isMemberLocationSaved(member.getIsMemberLocationSaved())
        .isMarketingInfoAgreed(member.getIsMarketingInfoAgreed())
        .isRequiredTermsAgreed(member.getIsRequiredTermsAgreed())
        .build();
  }

  /**
   * Firebase providerId를 SocialPlatform enum으로 변환
   *
   * @param providerId Firebase 소셜 로그인 제공자 ID
   * @return SocialPlatform
   */
  private SocialPlatform mapProviderIdToSocialPlatform(String providerId) {
    if (providerId == null) {
      throw new CustomException(ErrorCode.INVALID_SOCIAL_PLATFORM);
    }
    return switch (providerId) {
      case "google.com" -> SocialPlatform.GOOGLE;
      case "oidc.kakao", "kakao" -> SocialPlatform.KAKAO;
      case "apple.com" -> SocialPlatform.APPLE;
      default -> throw new CustomException(ErrorCode.INVALID_SOCIAL_PLATFORM);
    };
  }

  /**
   * refreshToken을 통해 accessToken을 재발급합니다
   *
   * @param request refreshToken
   * @return 재발급 된 accessToken
   */
  @Transactional
  public AuthResponse reissue(AuthRequest request) {

    log.debug("accessToken이 만료되어 토큰 재발급을 진행합니다.");

    String refreshToken = request.getRefreshToken();

    if (nvl(refreshToken, "").isBlank()) {
      log.error("refreshToken을 찾을 수 없습니다.");
      throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    try {
      if (!jwtUtil.validateToken(refreshToken)) {
        log.error("유효하지 않은 refreshToken 입니다.");
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
      }
    } catch (ExpiredJwtException e) { // #81 : 리프레시 토큰 만료시 400 에러 반환
      log.error("만료된 refreshToken 입니다: {}", e.getMessage());
      throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }

    CustomUserDetails customUserDetails = (CustomUserDetails) jwtUtil
        .getAuthentication(refreshToken).getPrincipal();
    String newAccessToken = jwtUtil.createAccessToken(customUserDetails);
    String newRefreshToken = jwtUtil.createRefreshToken(customUserDetails);

    Member member = memberRepository.findByEmail(jwtUtil.getUsername(newAccessToken))
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (member.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.error("정지 계정 토큰 재발급 시도: memberId={}", member.getMemberId());
      throw new SuspendedMemberException(member.getSuspendReason(), member.getSuspendedUntil());
    }

    // 새 RefreshToken을 Redis에 저장 (기존 RT 덮어쓰기로 자동 무효화)
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        newRefreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    return AuthResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .isItemCategorySaved(member.getIsItemCategorySaved())
        .isMemberLocationSaved(member.getIsMemberLocationSaved())
        .isMarketingInfoAgreed(member.getIsMarketingInfoAgreed())
        .isRequiredTermsAgreed(member.getIsRequiredTermsAgreed())
        .build();
  }

  /**
   * 로그아웃
   * 엑세스 토큰을 블랙리스트에 등록합니다
   * redis에 저장되어있는 리프레시토큰을 삭제합니다
   *
   * @param request accessToken, refreshToken
   */
  @Transactional
  public void logout(AuthRequest request) {

    Member member = request.getMember();
    String accessToken = request.getAccessToken();

    String key = REFRESH_KEY_PREFIX + member.getMemberId();
    jwtUtil.deactivateToken(accessToken, key);
  }
}
