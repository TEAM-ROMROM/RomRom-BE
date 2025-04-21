package com.romrom.romback.domain.service;

import static com.romrom.romback.global.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static com.romrom.romback.global.util.LogUtil.lineLog;
import static com.romrom.romback.global.util.LogUtil.superLogDebug;

import com.romrom.romback.domain.object.constant.AccountStatus;
import com.romrom.romback.domain.object.constant.Role;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.object.dto.TestRequest;
import com.romrom.romback.domain.object.dto.TestResponse;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.jwt.JwtUtil;
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
public class TestService {

  private final MemberRepository memberRepository;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder().locale("ko").uuidLength(4).numberLength(4).build();

  /**
   * 회원 이메일로 가짜 로그인 처리
   * 회원이 없으면 신규 가입 후, isFirstLogin 설정
   */
  @Transactional

  public TestResponse testSignIn(TestRequest request) {
    boolean isFirstLogin = false;
    String email = request.getEmail();

    // 이메일에 해당하는 회원 조회, 없으면 신규 가입 처리
    Member member = memberRepository.findByEmail(email)
        .orElseGet(() -> {

          Member newMember = Member.builder()
              .email(email)
              .socialPlatform(request.getSocialPlatform())
              .nickname(suhRandomKit.nicknameWithNumber())
              .profileUrl("TEST")
              .role(Role.ROLE_USER)
              .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
              .isFirstItemPosted(false)
              .build();

          Member savedMember = memberRepository.save(newMember);

          // 신규 가입시 첫 로그인으로 설정
          savedMember.setIsFirstLogin(true);
          return savedMember;
        });

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    lineLog("가짜 로그인 성공: email=" + email);
    superLogDebug(member);

    return TestResponse.builder()
        .member(member)
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .build();
  }
}
