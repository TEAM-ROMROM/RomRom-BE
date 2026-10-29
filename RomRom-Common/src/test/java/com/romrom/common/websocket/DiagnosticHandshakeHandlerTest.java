package com.romrom.common.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 핸드셰이크 거부 처리 검증 (#825): Upgrade 없는 요청 > 400 + Spring 기본 본문, 로그 값 정리
 */
class DiagnosticHandshakeHandlerTest {

  @Test
  @DisplayName("Upgrade 헤더 없는 GET > 400, 핸드셰이크 실패")
  void Upgrade_없는_요청은_400이다() {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/chat");
    servletRequest.addHeader("User-Agent", "Dart/3.5 (dart:io)");
    MockHttpServletResponse servletResponse = new MockHttpServletResponse();

    boolean isHandshakeAccepted = new DiagnosticHandshakeHandler().doHandshake(
        new ServletServerHttpRequest(servletRequest),
        new ServletServerHttpResponse(servletResponse),
        new TextWebSocketHandler(),
        new HashMap<>());

    assertFalse(isHandshakeAccepted);
    assertEquals(400, servletResponse.getStatus());
  }

  @Test
  @DisplayName("로그 값: 제어문자 > ?, 120자 제한, null 유지")
  void 로그_값을_정리한다() {
    assertEquals("a?b", DiagnosticHandshakeHandler.sanitize("a\nb"));
    assertEquals(120, DiagnosticHandshakeHandler.sanitize("x".repeat(500)).length());
    assertNull(DiagnosticHandshakeHandler.sanitize(null));
  }
}
