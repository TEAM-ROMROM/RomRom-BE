package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.jwt.JwtUtil;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

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

    // 가입된 회원이 존재하는지 확인
    if (memberRepository.findByEmail(email).isPresent()) {
      member = memberRepository.findByEmail(email).get();
    } else {
      isFirstLogin = true;
      member = memberRepository.save(Member.builder()
          .email(email)
          .nickname(nickname)
          .profileUrl(profileUrl)
          .socialPlatform(socialPlatform)
          .role(Role.ROLE_USER)
          .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
          .build()
      );
    }

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: email={}, accessToken={}, refreshToken={}", email, accessToken, refreshToken);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        "RT:" + customUserDetails.getMemberId(),
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
}
