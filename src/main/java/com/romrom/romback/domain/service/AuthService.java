package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.constant.SocialPlatform;
import com.romrom.romback.domain.object.dto.AuthRequest;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.OAuthMemberInfo;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.jwt.JwtUtil;
import com.romrom.romback.global.util.CommonUtil;
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
  private final OAuthService oAuthService;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;

  public AuthResponse signIn(AuthRequest request) {
    boolean isFirstLogin = false;

    // 소셜 플랫폼 -> 회원정보 정보 취득
    OAuthMemberInfo oAuthUserInfo = oAuthService.getMemberInfoFromOAuthPlatform(request);
    String email = oAuthUserInfo.getEmail();
    String profileUrl = oAuthUserInfo.getProfileUrl();
    String socialId = oAuthUserInfo.getSocialId();
    SocialPlatform socialPlatform = oAuthUserInfo.getSocialPlatform();

    // 가입된 회원이 존재하는지 확인
    // 회원이 없을 시 신규 가입 처리
    Member member = memberRepository.findByEmail(email)
        .orElseGet(() -> {
          Member newMember = Member.builder()
              .email(email)
              .nickname(CommonUtil.getRandomName())
              .profileUrl(profileUrl)
              .socialId(socialId)
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
        "RT:" + customUserDetails.getMemberId(),
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

}
