package com.romrom.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import com.romrom.common.service.SystemConfigCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

  // 점검 모드에서도 통과시킬 경로 (어드민, 버전체크, 헬스체크)
  private static final List<String> MAINTENANCE_WHITELIST = List.of(
      "/api/admin",
      "/api/app/version/check",
      "/actuator",
      "/admin"
  );

  private final SystemConfigCacheService systemConfigCacheService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {

    // Redis 캐시에서 점검 모드 활성화 여부를 읽어 옴 (기본값 false)
    String maintenanceEnabledValue = systemConfigCacheService.getOrDefault("server.maintenance.enabled", "false");

    if ("true".equals(maintenanceEnabledValue) && !isWhitelisted(request.getRequestURI())) {
      log.info("점검 모드 차단: {}", request.getRequestURI());
      sendMaintenanceResponse(response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  // 화이트리스트 경로인지 prefix 기준으로 판단
  private boolean isWhitelisted(String requestUri) {
    return MAINTENANCE_WHITELIST.stream().anyMatch(requestUri::startsWith);
  }

  // 503 점검 응답을 JSON 형태로 직접 작성 (Spring MVC 우회)
  private void sendMaintenanceResponse(HttpServletResponse response) throws IOException {
    response.setStatus(ErrorCode.MAINTENANCE_MODE.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    ErrorResponse maintenanceErrorResponse = ErrorResponse.builder()
        .errorCode(ErrorCode.MAINTENANCE_MODE)
        .errorMessage(ErrorCode.MAINTENANCE_MODE.getMessage())
        .build();

    response.getWriter().write(objectMapper.writeValueAsString(maintenanceErrorResponse));
  }
}
