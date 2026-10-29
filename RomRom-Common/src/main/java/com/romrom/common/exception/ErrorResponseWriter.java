package com.romrom.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * 필터 단계 JSON 에러 응답 작성 (GlobalExceptionHandler가 닿지 않는 구간 공통)
 * ObjectMapper는 JacksonConfig 빈 사용 (날짜 ISO 직렬화 등 설정 일치)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorResponseWriter {

  /**
   * ErrorCode > 상태코드 + ErrorResponse(errorCode, errorMessage)
   */
  public static void writeErrorResponse(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
      throws IOException {
    ErrorResponse errorResponse = ErrorResponse.builder()
        .errorCode(errorCode)
        .errorMessage(errorCode.getMessage())
        .build();
    writeJsonResponse(response, objectMapper, errorCode.getStatus(), errorResponse);
  }

  /**
   * 임의 응답 본문 JSON 작성 (정지 회원 응답 등 확장 필드용)
   */
  public static void writeJsonResponse(HttpServletResponse response, ObjectMapper objectMapper,
      HttpStatusCode status, Object responseBody) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getWriter(), responseBody);
  }
}
