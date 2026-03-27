package com.romrom.auth.service;

import static com.romrom.common.util.CommonUtil.nvl;

import com.google.firebase.auth.FirebaseToken;
import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.LoginRequest;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
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

  private static final String REFRESH_KEY_PREFIX = "RT:";
  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder().locale("ko").uuidLength(4).numberLength(4).build();

  private final MemberRepository memberRepository;
  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final FirebaseTokenVerifier firebaseTokenVerifier;

  /**
   * Firebase Authentication 기반 통합 로그인
   * Firebase ID Token을 검증하여 회원 조회 또는 신규 회원 생성 후 JWT를 발급합니다.
   *
   * @param request firebaseIdToken, providerId, profile, client
   */
  public AuthResponse login(LoginRequest request) {

    FirebaseToken firebaseToken = firebaseTokenVerifier.verify(request.getFirebaseIdToken());

    String email = firebaseToken.getEmail();
    String profileUrl = request.getProfile() != null ? request.getProfile().getPhotoUrl() : null;
    SocialPlatform socialPlatform = mapProviderIdToSocialPlatform(request.getProviderId());
    String nickname = suhRandomKit.nicknameWithNumber();

    log.debug("Firebase 로그인 시도: email={}, providerId={}, platform={}", email, request.getProviderId(), socialPlatform);

    Optional<Member> existMember = memberRepository.findByEmail(email);
    Member member;
    if (existMember.isPresent()) {
      member = existMember.get();
      if (member.getSocialPlatform() != socialPlatform) {
        throw new EmailAlreadyRegisteredException(member.getSocialPlatform());
      }
      member.setIsFirstLogin(false);
    } else { // 신규 회원
      member = Member.builder()
          .email(email)
          .nickname(nickname)
          .socialPlatform(socialPlatform)
          .profileUrl(profileUrl)
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
    memberRepository.save(member);

    // 정지 계정 처리
    if (member.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      if (member.getSuspendedUntil() != null && member.getSuspendedUntil().isBefore(LocalDateTime.now())) {
        // 정지 기간 만료 -> 자동 해제
        log.info("정지 기간 만료, 자동 해제: memberId={}", member.getMemberId());

        member.setAccountStatus(AccountStatus.ACTIVE_ACCOUNT);
        member.setSuspendReason(null);
        member.setSuspendedAt(null);
        member.setSuspendedUntil(null);
        memberRepository.save(member);

        // 제재 이력 자동 해제
        Optional<SanctionHistory> activeSanctionHistory = sanctionHistoryRepository
            .findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(member.getMemberId());
        if (activeSanctionHistory.isPresent()) {
          SanctionHistory sanctionToAutoLift = activeSanctionHistory.get();
          sanctionToAutoLift.setLiftedAt(LocalDateTime.now());
          sanctionToAutoLift.setLiftedReason("자동 해제 (기간 만료)");
          sanctionHistoryRepository.save(sanctionToAutoLift);
          log.debug("제재 이력 자동 해제: sanctionHistoryId={}", sanctionToAutoLift.getSanctionHistoryId());
        }
      } else {
        log.info("정지 계정 로그인 시도: memberId={}, suspendedUntil={}", member.getMemberId(), member.getSuspendedUntil());
        return AuthResponse.builder()
            .accessToken(null)
            .refreshToken(null)
            .accountStatus(member.getAccountStatus())
            .suspendReason(member.getSuspendReason())
            .suspendedUntil(member.getSuspendedUntil())
            .build();
      }
    }

    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: email={}, accessToken={}, refreshToken={}", email, accessToken, refreshToken);

    // RefreshToken Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

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
      case "oidc.kakao" -> SocialPlatform.KAKAO;
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

    Member member = memberRepository.findByEmail(jwtUtil.getUsername(newAccessToken))
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

    if (member.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT) {
      log.error("정지 계정 토큰 재발급 시도: memberId={}", member.getMemberId());
      throw new SuspendedMemberException(member.getSuspendReason(), member.getSuspendedUntil());
    }

    return AuthResponse.builder()
        .accessToken(newAccessToken)
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
