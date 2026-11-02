package com.romrom.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponseWriter;
import com.romrom.common.service.RequestRateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 호출 횟수 제한 필터: 규칙 5개(로그인 3종, 토큰 재발급, AI 가격 예측), 초과 시 429
 * 인증 필터 뒤에 둠 > 회원 기준 규칙에서 로그인 회원을 읽기 위함
 */
@Slf4j
@RequiredArgsConstructor
public class RequestRateLimitFilter extends OncePerRequestFilter {

  private static final Duration ONE_MINUTE_WINDOW = Duration.ofMinutes(1);

  // 프록시(DSM nginx)가 접속 주소로 덮어쓰는 헤더 > 클라이언트 위조 불가
  // X-Forwarded-For는 ForwardedHeaderFilter가 소비하고, 맨 앞 값은 클라이언트가 위조 가능
  private static final String REAL_CLIENT_IP_HEADER = "X-Real-IP";

  // 통신사 NAT로 여러 사용자가 IP를 공유할 수 있어 IP 기준 상한은 넉넉하게 설정
  private static final List<RateLimitRule> RATE_LIMIT_RULES = List.of(
      new RateLimitRule("member-login", "/api/auth/login", 30, RateLimitSubject.CLIENT_IP),
      new RateLimitRule("token-reissue", "/api/auth/reissue", 60, RateLimitSubject.CLIENT_IP),
      new RateLimitRule("kakao-firebase-token", "/api/auth/kakao/firebase-token", 30, RateLimitSubject.CLIENT_IP),
      new RateLimitRule("admin-login", "/api/admin/login", 10, RateLimitSubject.CLIENT_IP),
      // 호출마다 AI 비용 발생
      new RateLimitRule("item-price-predict", "/api/item/price/predict", 20, RateLimitSubject.MEMBER)
  );

  private final RequestRateLimitService requestRateLimitService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Optional<RateLimitRule> matchedRule = findMatchingRule(request);
    if (matchedRule.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    RateLimitRule rateLimitRule = matchedRule.get();
    // 기준값 없음(비로그인 회원 규칙) > 인증 단계에서 거부되므로 통과
    Optional<String> rateLimitSubject = resolveSubject(rateLimitRule, request);
    if (rateLimitSubject.isPresent() && !requestRateLimitService.isRequestAllowed(
        rateLimitRule.ruleName(), rateLimitSubject.get(), rateLimitRule.maximumRequestsPerMinute(), ONE_MINUTE_WINDOW)) {
      log.warn("[rate limit] rejected 429 : rule={}", rateLimitRule.ruleName());
      response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ONE_MINUTE_WINDOW.toSeconds()));
      ErrorResponseWriter.writeErrorResponse(response, objectMapper, ErrorCode.TOO_MANY_REQUESTS);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Optional<RateLimitRule> findMatchingRule(HttpServletRequest request) {
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return Optional.empty();
    }
    return RATE_LIMIT_RULES.stream()
        .filter(rateLimitRule -> rateLimitRule.requestPath().equals(request.getRequestURI()))
        .findFirst();
  }

  private Optional<String> resolveSubject(RateLimitRule rateLimitRule, HttpServletRequest request) {
    return rateLimitRule.subject() == RateLimitSubject.CLIENT_IP
        ? Optional.of(resolveClientIpAddress(request))
        : resolveAuthenticatedMemberId();
  }

  private String resolveClientIpAddress(HttpServletRequest request) {
    String realClientIpAddress = request.getHeader(REAL_CLIENT_IP_HEADER);
    return realClientIpAddress != null && !realClientIpAddress.isBlank()
        ? realClientIpAddress.trim()
        : request.getRemoteAddr();
  }

  private Optional<String> resolveAuthenticatedMemberId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails customUserDetails) {
      return Optional.of(customUserDetails.getMember().getMemberId().toString());
    }
    return Optional.empty();
  }

  private enum RateLimitSubject {
    CLIENT_IP,
    MEMBER
  }

  private record RateLimitRule(String ruleName, String requestPath, int maximumRequestsPerMinute, RateLimitSubject subject) {
  }
}
