package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.jwt.JwtUtil;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  @Value("${jwt.blacklist-prefix")
  private String blacklistPrefix;

  @Value("${jwt.refresh-key}")
  private String refreshTokenKey;

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
    String nickname = request.getNickname();
    String profileUrl = request.getProfileUrl();
    SocialPlatform socialPlatform = request.getSocialPlatform();

    boolean isFirstLogin = false;
    Member member;
    member = memberRepository.findByEmail(email);

    // 새로운 사용자인경우
    if (member == null) {
      member = memberRepository.save(Member.builder()
          .email(email)
          .nickname(nickname)
          .profileUrl(profileUrl)
          .socialPlatform(socialPlatform)
          .role(Role.ROLE_USER)
          .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
          .build()
      );
      isFirstLogin = true;
    }

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: email={}, accessToken={}, refreshToken={}", email, accessToken, refreshToken);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        refreshTokenKey + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .isFirstLogin(isFirstLogin)
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
    if (refreshToken == null || refreshToken.isBlank()) {
      log.error("refreshToken을 찾을 수 없습니다.");
      throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    } else if (jwtUtil.validateToken(refreshToken)) { // 리프레시 토큰이 유효하지 않은 경우
      log.error("유효하지 않은 refreshToken 입니다.");
      throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 새로운 accessToken, refreshToken 발급
    CustomUserDetails customUserDetails = (CustomUserDetails) jwtUtil
        .getAuthentication(refreshToken).getPrincipal();
    String newAccessToken = jwtUtil.createAccessToken(customUserDetails);

    return AuthResponse.builder()
        .accessToken(newAccessToken)
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

    // accessToken 블랙리스트 등록
    if (isTokenBlacklisted(accessToken)) {
      log.error("accessToken이 이미 블랙리스트에 등록되어있습니다. accessToken: {}", accessToken);
    } else {
      log.debug("accessToken을 블랙리스트에 등록합니다.");
      blacklistAccessToken(request.getAccessToken());
    }

    // 저장된 refreshToken 키
    String key = refreshTokenKey + member.getMemberId();

    // redis에 저장된 리프레시 토큰 삭제
    Boolean deleted = redisTemplate.delete(key);
    if (deleted) {
      log.debug("회원 : {} 리프레시 토큰 삭제 성공", member.getMemberId());
    } else { // 토큰이 이미 삭제되었거나, 존재하지 않는 경우
      log.debug("회원 : {} 리프레시 토큰을 찾을 수 없습니다.");
      throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }

  // accessToken을 블랙리스트에 등록합니다
  private void blacklistAccessToken(String accessToken) {
    String key = blacklistPrefix + accessToken;
    redisTemplate.opsForValue().set(
        key,
        "logout",
        jwtUtil.getRemainingValidationTime(accessToken),
        TimeUnit.MILLISECONDS);
  }

  // 해당 토큰이 블랙리스트에 있는지 확인합니다
  public Boolean isTokenBlacklisted(String accessToken) {
    String key = blacklistPrefix + accessToken;
    return redisTemplate.hasKey(key);
  }
}
