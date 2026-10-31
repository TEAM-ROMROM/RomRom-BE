package com.romrom.web.config;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.service.ActivityEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 앱 API 호출 1건 > API_CALL 이벤트 (컨트롤러 처리 요청만, 경로는 원본 대신 매핑 패턴으로 저장해 ID 미보관)
 * 대상 경로는 WebConfig 등록 범위(/api/**, 관리자 API 제외)
 */
@Component
@RequiredArgsConstructor
public class ApiCallActivityInterceptor implements HandlerInterceptor {

  static final String PROPERTY_HTTP_METHOD = "httpMethod";
  static final String PROPERTY_PATH_PATTERN = "pathPattern";
  static final String PROPERTY_STATUS_CODE = "statusCode";

  private final ActivityEventRecorder activityEventRecorder;

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
    if (!(handler instanceof HandlerMethod)) {
      return;
    }
    Map<String, Object> apiCallProperties = new HashMap<>();
    apiCallProperties.put(PROPERTY_HTTP_METHOD, request.getMethod());
    apiCallProperties.put(PROPERTY_PATH_PATTERN, resolvePathPattern(request));
    apiCallProperties.put(PROPERTY_STATUS_CODE, response.getStatus());
    activityEventRecorder.record(ActivityEventType.API_CALL, resolveAuthenticatedMemberId(), apiCallProperties);
  }

  // 매핑 패턴 (예: /api/item/{itemId}), 없으면 원본 경로
  private static String resolvePathPattern(HttpServletRequest request) {
    Object bestMatchingPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return bestMatchingPattern != null ? bestMatchingPattern.toString() : request.getRequestURI();
  }

  private static UUID resolveAuthenticatedMemberId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails customUserDetails
        && customUserDetails.getMember() != null) {
      return customUserDetails.getMember().getMemberId();
    }
    return null;
  }
}
