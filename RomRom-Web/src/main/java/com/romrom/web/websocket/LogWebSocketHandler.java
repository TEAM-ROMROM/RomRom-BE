package com.romrom.web.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.service.LogWebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 실시간 로그 스트리밍 WebSocket 핸들러
 * - 서버 → 클라이언트 단방향 broadcast (클라이언트 메시지는 무시)
 * - 인증은 handshake 인터셉터(JWT/HMAC)에서 이미 끝난 상태로 진입한다
 *
 * <p>/ws/admin-logs(관리자)와 /ws/debug-logs(Flutter)가 이 동일 핸들러를 공유한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogWebSocketHandler extends TextWebSocketHandler {

  private final LogWebSocketBroadcaster logWebSocketBroadcaster;
  private final ObjectMapper logCommandObjectMapper = new ObjectMapper();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    boolean isRegistered = logWebSocketBroadcaster.addSubscriber(session);
    if (!isRegistered) {
      // 최대 동시 구독자 초과 — 정책 위반 코드로 종료
      session.close(CloseStatus.POLICY_VIOLATION.withReason("최대 동시 접속 수 초과"));
      return;
    }
    // 연결 확인용 초기 메시지 (클라이언트가 연결 수립을 즉시 인지하도록)
    session.sendMessage(new TextMessage("{\"level\":\"INFO\",\"message\":\"connected\",\"source\":\"APP\"}"));
    log.info("실시간 로그 WebSocket 연결: sessionId={}, 활성 구독자={}",
        session.getId(), logWebSocketBroadcaster.getActiveSubscriberCount());
  }

  /**
   * 클라이언트 → 서버 제어 메시지 처리.
   * 현재 지원: AOP 상세 로그 토글 — {"action":"toggleAop","enabled":true|false}
   * 그 외 메시지는 무시한다 (로그 스트림은 기본적으로 서버→클라 단방향).
   */
  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonNode command;
    try {
      command = logCommandObjectMapper.readTree(message.getPayload());
    } catch (Exception e) {
      return; // JSON이 아니면 무시
    }
    if (command == null || !"toggleAop".equals(command.path("action").asText())) {
      return;
    }
    boolean enabled = command.path("enabled").asBoolean(false);
    logWebSocketBroadcaster.setAopEnabled(session, enabled);
    // 토글 결과를 해당 세션에 시스템 로그로 회신
    String ack = String.format(
        "{\"level\":\"INFO\",\"message\":\"AOP 상세 로그 %s\",\"source\":\"APP\"}",
        enabled ? "ON" : "OFF");
    session.sendMessage(new TextMessage(ack));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    logWebSocketBroadcaster.removeSubscriber(session);
    log.info("실시간 로그 WebSocket 종료: sessionId={}, status={}, 활성 구독자={}",
        session.getId(), status, logWebSocketBroadcaster.getActiveSubscriberCount());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    // 전송 오류 시 구독자 정리 (afterConnectionClosed가 함께 호출되지만 방어적으로 제거)
    logWebSocketBroadcaster.removeSubscriber(session);
  }
}
