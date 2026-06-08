package com.romrom.web.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 실시간 로그 스트리밍 WebSocket 설정 (순수 WebSocket)
 *
 * <p>채팅 STOMP({@code @EnableWebSocketMessageBroker})와 완전히 독립된 엔드포인트다.
 * RabbitMQ 릴레이를 타지 않고, 로그가 발생한 서버가 직접 구독자에게 broadcast 한다.
 *
 * <ul>
 *   <li>/ws/admin-logs — 관리자 웹 (JWT + ROLE_ADMIN + Origin 검증)</li>
 *   <li>/ws/debug-logs — Flutter 테스트앱 (HMAC 서명, 로그인 불필요)</li>
 * </ul>
 *
 * <p>두 엔드포인트는 동일한 {@link LogWebSocketHandler}와 broadcaster를 공유하고,
 * handshake 인증 방식만 다르다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class LogWebSocketConfig implements WebSocketConfigurer {

  private final LogWebSocketHandler logWebSocketHandler;
  private final JwtLogHandshakeInterceptor jwtLogHandshakeInterceptor;
  private final HmacLogHandshakeInterceptor hmacLogHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // 관리자 웹: 쿠키 JWT + ROLE_ADMIN + Origin 화이트리스트
    // setAllowedOrigins는 인터셉터에서 직접 검증하므로 "*"로 두되 인증은 인터셉터가 담당
    registry.addHandler(logWebSocketHandler, "/ws/admin-logs")
        .addInterceptors(jwtLogHandshakeInterceptor)
        .setAllowedOriginPatterns("*");

    // Flutter 테스트앱: HMAC 서명 (Origin 헤더 없는 네이티브 클라이언트라 Origin 검증 생략)
    registry.addHandler(logWebSocketHandler, "/ws/debug-logs")
        .addInterceptors(hmacLogHandshakeInterceptor)
        .setAllowedOriginPatterns("*");
  }
}
