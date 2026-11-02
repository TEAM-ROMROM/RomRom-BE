package com.romrom.web.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.service.RequestRateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestRateLimitFilterTest {

  private RequestRateLimitService requestRateLimitService;
  private RequestRateLimitFilter requestRateLimitFilter;
  private MockHttpServletResponse response;
  private MockFilterChain filterChain;

  @BeforeEach
  void setUp() {
    requestRateLimitService = mock(RequestRateLimitService.class);
    requestRateLimitFilter = new RequestRateLimitFilter(requestRateLimitService, new ObjectMapper());
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("제한 대상이 아닌 경로는 카운트하지 않고 통과한다")
  void 제한_대상이_아닌_경로는_통과한다() throws Exception {
    requestRateLimitFilter.doFilter(new MockHttpServletRequest("POST", "/api/item/get"), response, filterChain);

    assertNotNull(filterChain.getRequest());
    verifyNoInteractions(requestRateLimitService);
  }

  @Test
  @DisplayName("POST가 아니면 같은 경로라도 카운트하지 않는다")
  void POST가_아니면_통과한다() throws Exception {
    requestRateLimitFilter.doFilter(new MockHttpServletRequest("GET", "/api/admin/login"), response, filterChain);

    assertNotNull(filterChain.getRequest());
    verifyNoInteractions(requestRateLimitService);
  }

  @Test
  @DisplayName("상한 초과 시 429와 Retry-After를 응답하고 체인을 중단한다")
  void 상한_초과_시_429를_응답한다() throws Exception {
    when(requestRateLimitService.isRequestAllowed(anyString(), anyString(), anyInt(), any())).thenReturn(false);

    requestRateLimitFilter.doFilter(new MockHttpServletRequest("POST", "/api/admin/login"), response, filterChain);

    assertEquals(429, response.getStatus());
    assertEquals("60", response.getHeader("Retry-After"));
    assertNull(filterChain.getRequest());
  }

  @Test
  @DisplayName("IP 기준 규칙은 위조 가능한 X-Forwarded-For가 아니라 X-Real-IP를 쓴다")
  void IP_기준_규칙은_X_Real_IP를_쓴다() throws Exception {
    when(requestRateLimitService.isRequestAllowed(anyString(), anyString(), anyInt(), any())).thenReturn(true);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.addHeader("X-Forwarded-For", "1.1.1.1");
    request.addHeader("X-Real-IP", "2.2.2.2");

    requestRateLimitFilter.doFilter(request, response, filterChain);

    verify(requestRateLimitService).isRequestAllowed(eq("member-login"), eq("2.2.2.2"), anyInt(), any());
    assertNotNull(filterChain.getRequest());
  }

  @Test
  @DisplayName("회원 기준 규칙은 비로그인이면 카운트 없이 인증 단계로 넘긴다")
  void 회원_기준_규칙은_비로그인이면_통과한다() throws Exception {
    requestRateLimitFilter.doFilter(new MockHttpServletRequest("POST", "/api/item/price/predict"), response, filterChain);

    assertNotNull(filterChain.getRequest());
    verifyNoInteractions(requestRateLimitService);
  }
}
