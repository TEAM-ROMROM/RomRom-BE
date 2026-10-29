package com.romrom.common.websocket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * WebSocket 핸드셰이크 거부 로그: Spring 기본 ERROR 한 줄 > WARN + 진단 정보 (#825)
 * 진단 4개: 경로, Upgrade·Connection 헤더, User-Agent, 클라이언트 IP. 응답(400·본문)은 Spring 기본과 동일
 */
@Slf4j
public class DiagnosticHandshakeHandler extends DefaultHandshakeHandler {

  // 프록시가 넘기는 원 클라이언트 IP 헤더 (Traefik·nginx)
  private static final List<String> CLIENT_IP_HEADERS = List.of("X-Forwarded-For", "X-Real-IP");
  // 로그에 남길 헤더 값 최대 길이
  private static final int MAX_LOGGED_VALUE_LENGTH = 120;

  @Override
  protected void handleInvalidUpgradeHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
    logRejectedHandshake("invalid Upgrade header", request);
    writeBadRequest(response, "Can \"Upgrade\" only to \"WebSocket\".");
  }

  @Override
  protected void handleInvalidConnectHeader(ServerHttpRequest request, ServerHttpResponse response) throws IOException {
    logRejectedHandshake("invalid Connection header", request);
    writeBadRequest(response, "\"Connection\" must be \"upgrade\".");
  }

  private void logRejectedHandshake(String reason, ServerHttpRequest request) {
    HttpHeaders headers = request.getHeaders();
    log.warn("[websocket handshake] rejected 400 : {} path={}, upgrade={}, connection={}, userAgent={}, clientIp={}",
        reason,
        sanitize(request.getURI().getPath()),
        sanitize(headers.getFirst(HttpHeaders.UPGRADE)),
        sanitize(headers.getFirst(HttpHeaders.CONNECTION)),
        sanitize(headers.getFirst(HttpHeaders.USER_AGENT)),
        sanitize(resolveClientIp(request)));
  }

  /**
   * 원 클라이언트 IP: 프록시 헤더 우선 > 소켓 주소
   */
  private String resolveClientIp(ServerHttpRequest request) {
    for (String clientIpHeader : CLIENT_IP_HEADERS) {
      String headerValue = request.getHeaders().getFirst(clientIpHeader);
      if (headerValue != null && !headerValue.isBlank()) {
        return headerValue;
      }
    }
    return request.getRemoteAddress() != null ? request.getRemoteAddress().getHostString() : null;
  }

  /**
   * 로그용 값 정리 (제어문자·비ASCII > ?, 120자 제한)
   */
  static String sanitize(String rawValue) {
    if (rawValue == null) {
      return null;
    }
    String printableValue = rawValue.replaceAll("[^\\x20-\\x7E]", "?");
    return printableValue.length() > MAX_LOGGED_VALUE_LENGTH
        ? printableValue.substring(0, MAX_LOGGED_VALUE_LENGTH)
        : printableValue;
  }

  private void writeBadRequest(ServerHttpResponse response, String responseMessage) throws IOException {
    response.setStatusCode(HttpStatus.BAD_REQUEST);
    response.getBody().write(responseMessage.getBytes(StandardCharsets.UTF_8));
  }
}
