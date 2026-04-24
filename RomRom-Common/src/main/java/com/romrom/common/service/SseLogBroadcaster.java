package com.romrom.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.romrom.common.dto.DebugLogEvent;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 로그 스트리밍 구독자 관리 및 브로드캐스트
 * - 최대 동시 구독자: 10
 * - 초당 로그 이벤트 제한: 100건
 */
@Component
public class SseLogBroadcaster {

  private static final int MAX_SUBSCRIBERS = 10;
  private static final int MAX_EVENTS_PER_SECOND = 100;

  private final CopyOnWriteArrayList<SseEmitter> debugLogSubscribers = new CopyOnWriteArrayList<>();
  private final ObjectMapper debugLogObjectMapper;

  private final AtomicInteger eventsInCurrentSecond = new AtomicInteger(0);
  private final AtomicLong currentSecondTimestamp = new AtomicLong(0);
  private final AtomicInteger skippedEventCount = new AtomicInteger(0);

  public SseLogBroadcaster() {
    this.debugLogObjectMapper = new ObjectMapper();
    this.debugLogObjectMapper.registerModule(new JavaTimeModule());
  }

  /**
   * SSE 구독자 등록
   * @return 등록 성공 여부 (최대 구독자 초과 시 false)
   */
  public boolean addSubscriber(SseEmitter sseLogEmitter) {
    if (debugLogSubscribers.size() >= MAX_SUBSCRIBERS) {
      return false;
    }
    debugLogSubscribers.add(sseLogEmitter);
    return true;
  }

  /**
   * SSE 구독자 제거
   */
  public void removeSubscriber(SseEmitter sseLogEmitter) {
    debugLogSubscribers.remove(sseLogEmitter);
  }

  /**
   * 모든 구독자에게 로그 이벤트 브로드캐스트
   * - 초당 약 100건 제한 (근사치 rate limiting — 멀티스레드 환경에서 정확한 초당 제한이 아닌 대략적 제한)
   * - 초과 시 건너뛰고 "[N건 생략]" 메시지 전송
   */
  public void broadcast(DebugLogEvent debugLogEvent) {
    if (debugLogSubscribers.isEmpty()) {
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

    String debugLogJson;
    try {
      debugLogJson = debugLogObjectMapper.writeValueAsString(debugLogEvent);
    } catch (IOException e) {
      return;
    }

    for (SseEmitter subscriberEmitter : debugLogSubscribers) {
      try {
        subscriberEmitter.send(SseEmitter.event().data(debugLogJson));
      } catch (IOException e) {
        debugLogSubscribers.remove(subscriberEmitter);
      }
    }
  }

  /**
   * 생략된 로그 건수 알림 전송
   */
  private void sendSkippedNotification(int skippedLogCount) {
    String skippedMessage = String.format("{\"level\":\"WARN\",\"message\":\"[%d건 생략 — rate limit 초과]\"}", skippedLogCount);
    for (SseEmitter subscriberEmitter : debugLogSubscribers) {
      try {
        subscriberEmitter.send(SseEmitter.event().data(skippedMessage));
      } catch (IOException e) {
        debugLogSubscribers.remove(subscriberEmitter);
      }
    }
  }

  /**
   * 현재 활성 구독자 수 반환
   */
  public int getActiveSubscriberCount() {
    return debugLogSubscribers.size();
  }
}
