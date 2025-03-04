package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.CommonUtil.nvl;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

  private static final String REFRESH_KEY_PREFIX = "RT:";

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

    // 회원이 없을 시 신규 가입 처리
    Member member = memberRepository.findByEmail(email)
        .orElseGet(() -> {
          Member newMember = Member.builder()
              .email(email)
              .nickname(nickname)
              .profileUrl(profileUrl)
              .socialPlatform(socialPlatform)
              .role(Role.ROLE_USER)
              .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
              .build();

          // 신규 회원 저장
          Member savedMember = memberRepository.save(newMember);

          // 첫 로그인 처리
          savedMember.setIsFirstLogin(true);

          return savedMember;
        });

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
    } else if (!jwtUtil.validateToken(refreshToken)) { // 리프레시 토큰이 유효하지 않은 경우
      log.error("유효하지 않은 refreshToken 입니다.");
      throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 새로운 accessToken
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
    if (jwtUtil.isTokenBlacklisted(accessToken)) {
      log.error("accessToken이 이미 블랙리스트에 등록되어있습니다. accessToken: {}", accessToken);
    } else {
      log.debug("accessToken을 블랙리스트에 등록합니다.");
      jwtUtil.blacklistAccessToken(request.getAccessToken());
    }

    // 저장된 refreshToken 키
    String key = REFRESH_KEY_PREFIX + member.getMemberId();

    // redis에 저장된 리프레시 토큰 삭제
    Boolean isDeleted = redisTemplate.delete(key);
    if (isDeleted) {
      log.debug("회원 : {} 리프레시 토큰 삭제 성공", member.getMemberId());
    } else { // 토큰이 이미 삭제되었거나, 존재하지 않는 경우
      log.debug("회원 : {} 리프레시 토큰을 찾을 수 없습니다.", member.getMemberId());
      throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
  }
}
