package com.romrom.romback.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.romback.domain.object.dto.AuthResponse;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.dto.CustomUserDetails;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
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

  // 로그인 성공 시, accessToken과 refreshToken을 JSON 응답 바디에 담아서 전송
  @Override
  protected void successfulAuthentication(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain,
      Authentication authentication) throws IOException {

    // 인증 성공 후 CustomUserDetails 획득
    CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
    Member member = customUserDetails.getMember();

    // JWT 토큰 생성
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    log.debug("로그인 성공: accessToken = {}", accessToken);
    log.debug("로그인 성공: refreshToken = {}", refreshToken);

    // RefreshToken을 Redis에 저장 (key: RT:memberId)
    redisTemplate.opsForValue().set(
        "RT:" + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    // JSON 형태로 응답 바디에 accessToken과 refreshToken 전달
    AuthResponse authResponse = AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .build();

    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(response.getWriter(), authResponse);
  }

  // 로그인 실패 시 처리
  @Override
  protected void unsuccessfulAuthentication(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException failed) {
    log.error("로그인 실패");
    throw new CustomException(ErrorCode.UNAUTHORIZED);
  }
}
