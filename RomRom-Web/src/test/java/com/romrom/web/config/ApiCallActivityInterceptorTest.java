package com.romrom.web.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.service.ActivityEventRecorder;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

/**
 * API 호출 이벤트 인터셉터 단위 테스트: 컨트롤러 요청만 기록, 매핑 패턴·상태 코드·로그인 회원 담기
 */
@ExtendWith(MockitoExtension.class)
class ApiCallActivityInterceptorTest {

  @Mock
  private ActivityEventRecorder activityEventRecorder;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  // 컨트롤러 메서드 대역
  public void sampleControllerMethod() {
  }

  @Test
  @DisplayName("컨트롤러 요청이면 매핑 패턴·메서드·상태 코드·회원 ID로 API_CALL 기록")
  @SuppressWarnings("unchecked")
  void 컨트롤러_요청_기록() throws Exception {
    Member loginMember = Member.builder().memberId(UUID.randomUUID()).build();
    CustomUserDetails loginUserDetails = new CustomUserDetails(loginMember);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(loginUserDetails, null, List.of()));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/item/1234");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/item/{itemId}");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);
    HandlerMethod controllerHandler = new HandlerMethod(this, getClass().getMethod("sampleControllerMethod"));

    new ApiCallActivityInterceptor(activityEventRecorder).afterCompletion(request, response, controllerHandler, null);

    ArgumentCaptor<Map<String, Object>> propertiesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(activityEventRecorder).record(eq(ActivityEventType.API_CALL), eq(loginMember.getMemberId()), propertiesCaptor.capture());
    Assertions.assertEquals("/api/item/{itemId}", propertiesCaptor.getValue().get(ApiCallActivityInterceptor.PROPERTY_PATH_PATTERN));
    Assertions.assertEquals("POST", propertiesCaptor.getValue().get(ApiCallActivityInterceptor.PROPERTY_HTTP_METHOD));
    Assertions.assertEquals(200, propertiesCaptor.getValue().get(ApiCallActivityInterceptor.PROPERTY_STATUS_CODE));
  }

  @Test
  @DisplayName("정적 리소스 등 컨트롤러가 아닌 요청은 기록하지 않음")
  void 컨트롤러_아닌_요청_무시() {
    new ApiCallActivityInterceptor(activityEventRecorder).afterCompletion(
        new MockHttpServletRequest("GET", "/api/static.js"), new MockHttpServletResponse(), new ResourceHttpRequestHandler(), null);

    verify(activityEventRecorder, never()).record(any(), any(), any());
  }
}
