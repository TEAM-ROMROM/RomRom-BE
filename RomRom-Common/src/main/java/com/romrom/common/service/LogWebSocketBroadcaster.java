package com.romrom.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.romrom.common.dto.DebugLogEvent;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 실시간 로그 WebSocket 구독자 관리 및 브로드캐스트
 * - 전송 매체: WebSocketSession (기존 SSE 방식에서 전환)
 * - 최대 동시 구독자: 10
 * - 초당 로그 이벤트 제한: 100건 (로그 폭주로 세션이 막히는 것 방지)
 *
 * <p>관리자 웹(/ws/admin-logs)과 Flutter 테스트앱(/ws/debug-logs)이 동일한 broadcaster를 공유한다.
 * handshake 인증 방식만 다를 뿐, 연결된 세션은 모두 같은 로그 스트림을 받는다.
 */
@Component
@Slf4j
public class LogWebSocketBroadcaster {

  private static final int MAX_SUBSCRIBERS = 10;
  private static final int MAX_EVENTS_PER_SECOND = 100;

  // AOP 상세 로그(@LogMonitor 등 suhlogger) 출처 식별자 — DebugLogEvent.source와 일치
  public static final String SOURCE_AOP = "AOP";

  // 동시 구독 세션 목록 (관리자 + 디버그앱 통합)
  private final CopyOnWriteArrayList<WebSocketSession> logSubscriberSessions = new CopyOnWriteArrayList<>();
  // 세션별 AOP 상세 로그 수신 토글 (기본 false — 폭주 방지). 켠 세션에만 AOP 로그 전송
  private final Map<WebSocketSession, Boolean> aopEnabledBySession = new ConcurrentHashMap<>();
  private final ObjectMapper logEventObjectMapper;

  // rate limiting 상태 (근사치 — 멀티스레드 환경에서 정확한 초당 제한이 아닌 대략적 제한)
  private final AtomicInteger eventsInCurrentSecond = new AtomicInteger(0);
  private final AtomicLong currentSecondTimestamp = new AtomicLong(0);
  private final AtomicInteger skippedEventCount = new AtomicInteger(0);

  public LogWebSocketBroadcaster() {
    this.logEventObjectMapper = new ObjectMapper();
    this.logEventObjectMapper.registerModule(new JavaTimeModule());
  }

  /**
   * WebSocket 구독자 등록
   * @return 등록 성공 여부 (최대 구독자 초과 시 false)
   */
  public boolean addSubscriber(WebSocketSession logSubscriberSession) {
    if (logSubscriberSessions.size() >= MAX_SUBSCRIBERS) {
      return false;
    }
    logSubscriberSessions.add(logSubscriberSession);
    aopEnabledBySession.put(logSubscriberSession, false); // 기본 OFF — 폭주 방지
    return true;
  }

  /**
   * WebSocket 구독자 제거
   */
  public void removeSubscriber(WebSocketSession logSubscriberSession) {
    logSubscriberSessions.remove(logSubscriberSession);
    aopEnabledBySession.remove(logSubscriberSession);
  }

  /**
   * 특정 세션의 AOP 상세 로그 수신 여부 토글.
   * 클라이언트가 토글 버튼을 누르면 WebSocket 메시지로 이 메서드가 호출된다.
   */
  public void setAopEnabled(WebSocketSession session, boolean enabled) {
    if (logSubscriberSessions.contains(session)) {
      aopEnabledBySession.put(session, enabled);
    }
  }

  /**
   * 모든 구독자에게 로그 이벤트 브로드캐스트
   * - 초당 약 100건 제한 (근사치 rate limiting)
   * - 초과 시 건너뛰고 "[N건 생략]" 메시지를 다음 초에 전송
   */
  public void broadcast(DebugLogEvent debugLogEvent) {
    if (logSubscriberSessions.isEmpty()) {
      return;
    }

    // Rate limiting: 초당 이벤트 수 체크
    long nowSeconds = System.currentTimeMillis() / 1000;
    long previousSecond = currentSecondTimestamp.getAndSet(nowSeconds);
    if (nowSeconds != previousSecond) {
      // 새로운 초가 시작됨 — 생략된 건수가 있으면 알림 전송
      int skippedInPreviousSecond = skippedEventCount.getAndSet(0);
      if (skippedInPreviousSecond > 0) {
        sendSkippedNotification(skippedInPreviousSecond);
      }
      eventsInCurrentSecond.set(0);
    }

    if (eventsInCurrentSecond.incrementAndGet() > MAX_EVENTS_PER_SECOND) {
      skippedEventCount.incrementAndGet();
      return;
    }

    String logEventJson;
    try {
      logEventJson = logEventObjectMapper.writeValueAsString(debugLogEvent);
    } catch (IOException e) {
      return;
    }

    // AOP 상세 로그는 토글을 켠 세션에만 전송 (평소 폭주 방지). 일반 로그는 전체 전송.
    boolean isAopLog = SOURCE_AOP.equals(debugLogEvent.getSource());
    sendToSessions(logEventJson, isAopLog);
  }

  /**
   * 생략된 로그 건수 알림 전송 (rate limit 초과 시) — 일반 로그로 전체 전송
   */
  private void sendSkippedNotification(int skippedLogCount) {
    String skippedMessage = String.format(
        "{\"level\":\"WARN\",\"message\":\"[%d건 생략 — rate limit 초과]\",\"source\":\"APP\"}", skippedLogCount);
    sendToSessions(skippedMessage, false);
  }

  /**
   * 활성 세션에 텍스트 메시지 전송. 전송 실패한 세션은 목록에서 제거.
   * WebSocketSession.sendMessage는 동시 호출에 안전하지 않으므로 세션별로 동기화한다.
   *
   * @param onlyAopEnabled true면 AOP 토글을 켠 세션에만 전송 (AOP 상세 로그용)
   */
  private void sendToSessions(String payload, boolean onlyAopEnabled) {
    for (WebSocketSession subscriberSession : logSubscriberSessions) {
      // AOP 로그는 토글 켠 세션만 대상
      if (onlyAopEnabled && !Boolean.TRUE.equals(aopEnabledBySession.get(subscriberSession))) {
        continue;
      }
      try {
        if (!subscriberSession.isOpen()) {
          removeSubscriber(subscriberSession);
          continue;
        }
        // 동일 세션에 대한 동시 sendMessage 충돌 방지
        synchronized (subscriberSession) {
          subscriberSession.sendMessage(new TextMessage(payload));
        }
      } catch (IOException e) {
        // 전송 실패 = 끊긴 세션. 목록에서 제거하고 닫기 시도
        removeSubscriber(subscriberSession);
        closeQuietly(subscriberSession);
      }
    }
  }

  private void closeQuietly(WebSocketSession session) {
    try {
      session.close();
    } catch (IOException ignored) {
      // 이미 닫힌 세션 — 무시
    }
  }

  /**
   * 현재 활성 구독자 수 반환
   */
  public int getActiveSubscriberCount() {
    return logSubscriberSessions.size();
  }
}
