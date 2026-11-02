package com.romrom.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 느린 요청 로그: 기준 시간 초과 요청의 메서드·경로·상태·소요 시간 기록
 * 쿼리 문자열은 남기지 않음 (토큰 등 민감값 차단)
 */
@Slf4j
public class SlowRequestLoggingFilter extends OncePerRequestFilter {

  // 느린 요청 기준 (1초)
  static final long SLOW_REQUEST_THRESHOLD_MILLISECONDS = 1_000;
  // 로그에 남길 경로 최대 길이
  private static final int MAXIMUM_LOGGED_PATH_LENGTH = 100;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAtMilliseconds = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      // 기준 초과 시 기록 (성공·실패 공통)
      long elapsedMilliseconds = System.currentTimeMillis() - startedAtMilliseconds;
      if (elapsedMilliseconds >= SLOW_REQUEST_THRESHOLD_MILLISECONDS) {
        log.warn("[slow request] {} {} status={} elapsedMilliseconds={}",
            request.getMethod(), toSafeLogPath(request.getRequestURI()), response.getStatus(), elapsedMilliseconds);
      }
    }
  }

  /**
   * 외부 입력 경로 정리 (공백·제어문자·비ASCII > ?, 100자 제한)
   */
  static String toSafeLogPath(String requestPath) {
    String safePath = requestPath.replaceAll("[^\\x21-\\x7E]", "?");
    return safePath.length() > MAXIMUM_LOGGED_PATH_LENGTH ? safePath.substring(0, MAXIMUM_LOGGED_PATH_LENGTH) : safePath;
  }
}
