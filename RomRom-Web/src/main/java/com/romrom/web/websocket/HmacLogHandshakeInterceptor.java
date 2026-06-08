package com.romrom.web.websocket;

import com.romrom.common.properties.SecuredApiProperties;
import com.romrom.common.util.SignatureUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Flutter 테스트앱 실시간 로그 WebSocket(/ws/debug-logs) 핸드셰이크 인증 인터셉터
 *
 * <p>로그인(JWT) 없이도 동작해야 하므로 기존 @SecuredApi와 동일한 HMAC 서명 방식을 사용한다.
 * 핸드셰이크 HTTP 헤더의 X-Timestamp / X-Signature를 검증한다 (SecuredApiAspect 로직과 동일).
 * 운영 빌드에는 secret key가 없으므로 호출 자체가 불가능하다.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SecuredApiProperties.class)
@Slf4j
public class HmacLogHandshakeInterceptor implements HandshakeInterceptor {

  private static final String HEADER_SIGNATURE = "X-Signature";
  private static final String HEADER_TIMESTAMP = "X-Timestamp";

  private final SecuredApiProperties securedApiProperties;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attributes) {

    String signature = request.getHeaders().getFirst(HEADER_SIGNATURE);
    String timestamp = request.getHeaders().getFirst(HEADER_TIMESTAMP);

    // 헤더 누락 검증
    if (signature == null || signature.isBlank() || timestamp == null || timestamp.isBlank()) {
      log.warn("디버그 로그 WebSocket 핸드셰이크 거부 — 서명 헤더 누락");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    // Timestamp 만료 검증 (양방향: 클라이언트 시계 오차 허용)
    long requestTimestamp;
    try {
      requestTimestamp = Long.parseLong(timestamp);
    } catch (NumberFormatException e) {
      log.warn("디버그 로그 WebSocket 핸드셰이크 거부 — 타임스탬프 형식 오류: {}", timestamp);
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }
    long currentTime = System.currentTimeMillis();
    if (Math.abs(currentTime - requestTimestamp) > securedApiProperties.getExpirationTime()) {
      log.warn("디버그 로그 WebSocket 핸드셰이크 거부 — 타임스탬프 만료");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    // HMAC 서명 검증
    if (!SignatureUtil.verifySignature(timestamp, securedApiProperties.getSecretKey(), signature)) {
      log.warn("디버그 로그 WebSocket 핸드셰이크 거부 — 서명 불일치");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Exception exception) {
    // no-op
  }
}
