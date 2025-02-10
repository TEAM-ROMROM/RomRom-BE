package com.romrom.romback.global.filter;

import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@RequiredArgsConstructor
@Slf4j
public class LoginFilter extends UsernamePasswordAuthenticationFilter {

  private final JwtUtil jwtUtil;
  private final AuthenticationManager authenticationManager;
  private final RedisTemplate<String, Object> redisTemplate;
  private final MemberRepository memberRepository;

  @Override
  public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {

    // 클라이언트 요청에서 username, password 추출
    String username = obtainUsername(request);
    String password = obtainPassword(request);

    UsernamePasswordAuthenticationToken authToken =
        new UsernamePasswordAuthenticationToken(username, password, null);

    return authenticationManager.authenticate(authToken);
  }

  // 로그인 성공 (JWT 발급)
  @Override
  protected void successfulAuthentication(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      Authentication authentication) throws IOException {

    // CustomUserDetails
    CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
    Member member = customUserDetails.getMember();
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: 엑세스 토큰 및 리프레시 토큰 생성");
    log.debug("accessToken = {}", accessToken);
    log.debug("refreshToken = {}", refreshToken);

    // RefreshToken을 Redisd에 저장 (key: RT:memberId)
    redisTemplate.opsForValue().set(
        "RT:" + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    // 헤더에 accessToken 추가
    response.setHeader("Authorization", "Bearer " + accessToken);

    // 쿠키에 refreshToken 추가
    Cookie cookie = new Cookie("refreshToken", refreshToken);
    cookie.setHttpOnly(true); // HttpOnly 설정
    cookie.setSecure(false); // FIXME: HTTPS 환경에서는 secure 속성 true로 설정 (현재는 HTTP)
    cookie.setPath("/");
    cookie.setMaxAge((int) (jwtUtil.getRefreshExpirationTime() / 1000)); // 쿠키 maxAge는 초 단위 이므로, 밀리초를 1000으로 나눔
    response.addCookie(cookie);
  }

  // 로그인 실패
  @Override
  protected void unsuccessfulAuthentication(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException failed) {
    log.error("로그인 실패");
    throw new CustomException(ErrorCode.UNAUTHORIZED);
  }
}
