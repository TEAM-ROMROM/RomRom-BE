package com.romrom.auth.service;

import static com.romrom.common.util.CommonUtil.nvl;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.Role;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import io.jsonwebtoken.ExpiredJwtException;
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
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * 로그인 로직
   * 클라이언트로부터 플랫폼, 닉네임, 프로필url, 이메일을 입력받아 JWT를 발급합니다.
   *
   * @param request socialPlatform, email, nickname, profileUrl
   */
  public AuthResponse signIn(AuthRequest request) {

    // 요청 값으로부터 사용자 정보 획득
    String email = request.getEmail();
    String nickname = suhRandomKit.nicknameWithNumber(); // 랜덤 닉네임 생성 : 2025.04.21 : #121
    String profileUrl = request.getProfileUrl();
    SocialPlatform socialPlatform = request.getSocialPlatform();

    // 회원 조회
    Optional<Member> existMember = memberRepository.findByEmail(email);
    Member member;
    if (existMember.isPresent()) {
      member = existMember.get();
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
          .isNotificationAgreed(false)
          .isDeleted(false)
          .totalLikeCount(0)
          .build();
    }
    memberRepository.save(member);

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: email={}, accessToken={}, refreshToken={}", email, accessToken, refreshToken);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .isItemCategorySaved(member.getIsItemCategorySaved())
        .isMemberLocationSaved(member.getIsMemberLocationSaved())
        .isMarketingInfoAgreed(member.getIsMarketingInfoAgreed())
        .isRequiredTermsAgreed(member.getIsRequiredTermsAgreed())
        .build();
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

    // 리프레시 토큰이 없는 경우
    if (nvl(refreshToken, "").isBlank()) {
      log.error("refreshToken을 찾을 수 없습니다.");
      throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // 리프레시 토큰 유효성 검사 및 만료 여부 확인
    try {
      if (!jwtUtil.validateToken(refreshToken)) {
        log.error("유효하지 않은 refreshToken 입니다.");
        throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
      }
    } catch (ExpiredJwtException e) { // #81 : 리프레시 토큰 만료시 400 에러 반환
      log.error("만료된 refreshToken 입니다: {}", e.getMessage());
      throw new CustomException(ErrorCode.EXPIRED_REFRESH_TOKEN);
    }

    // 새로운 accessToken 생성
    CustomUserDetails customUserDetails = (CustomUserDetails) jwtUtil
        .getAuthentication(refreshToken).getPrincipal();
    String newAccessToken = jwtUtil.createAccessToken(customUserDetails);

    Member member = memberRepository.findByEmail(jwtUtil.getUsername(newAccessToken))
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

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

    // 저장된 refreshToken 키
    String key = REFRESH_KEY_PREFIX + member.getMemberId();

    // 토큰 비활성화
    jwtUtil.deactivateToken(accessToken, key);
  }
}
