package com.romrom.common.exception.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 프레임워크 요청 오류 4xx 유지 검증 (#814): catch-all이 500으로 바꾸던 문제
 */
class GlobalExceptionHandlerClientErrorTest {

  private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/not-exist");

  @Test
  @DisplayName("없는 경로는 404 API_NOT_FOUND")
  void 없는_경로는_404다() {
    ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(
        new NoResourceFoundException(HttpMethod.GET, "api/not-exist"), request);

    assertEquals(404, response.getStatusCode().value());
    assertEquals(ErrorCode.API_NOT_FOUND, response.getBody().getErrorCode());
  }

  @Test
  @DisplayName("지원하지 않는 메서드는 405 METHOD_NOT_ALLOWED")
  void 지원하지_않는_메서드는_405다() {
    ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(
        new HttpRequestMethodNotSupportedException("GET", List.of("POST")), request);

    assertEquals(405, response.getStatusCode().value());
    assertEquals(ErrorCode.METHOD_NOT_ALLOWED, response.getBody().getErrorCode());
  }

  @Test
  @DisplayName("읽을 수 없는 본문은 400 INVALID_REQUEST")
  void 읽을_수_없는_본문은_400이다() {
    ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(
        new HttpMessageNotReadableException("bad json", new ServletServerHttpRequest(request)), request);

    assertEquals(400, response.getStatusCode().value());
    assertEquals(ErrorCode.INVALID_REQUEST, response.getBody().getErrorCode());
  }

  @Test
  @DisplayName("그 외 예외는 기존대로 500")
  void 그_외_예외는_500이다() {
    ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleException(
        new IllegalStateException("boom"), request);

    assertEquals(500, response.getStatusCode().value());
    assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, response.getBody().getErrorCode());
  }
}
