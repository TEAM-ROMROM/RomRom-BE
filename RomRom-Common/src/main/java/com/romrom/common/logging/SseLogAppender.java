package com.romrom.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.romrom.common.dto.DebugLogEvent;
import com.romrom.common.service.SseLogBroadcaster;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationContext;

/**
 * Logback 커스텀 Appender — 로그 이벤트를 SSE 구독자에게 발행
 * com.romrom 패키지 로그만 필터링하여 전송
 */
public class SseLogAppender extends AppenderBase<ILoggingEvent> {

  private static final String TARGET_LOGGER_PREFIX = "com.romrom";

  // SSE 내부 클래스 로그는 FE에 전송하지 않음 (피드백 루프 방지)
  private static final String SSE_BROADCASTER_LOGGER = "com.romrom.common.service.SseLogBroadcaster";
  private static final String SSE_CONTROLLER_LOGGER = "com.romrom.web.controller.api.DebugController";

  private static volatile ApplicationContext applicationContext;
  private volatile SseLogBroadcaster sseLogBroadcaster;

  /**
   * Spring ApplicationContext 설정 (ApplicationContextAware 또는 초기화 시 호출)
   */
  public static void setApplicationContext(ApplicationContext applicationContext) {
    SseLogAppender.applicationContext = applicationContext;
  }

  @Override
  protected void append(ILoggingEvent loggingEvent) {
    // com.romrom 패키지 로그만 처리
    String loggerName = loggingEvent.getLoggerName();
    if (!loggerName.startsWith(TARGET_LOGGER_PREFIX)) {
      return;
    }

    // SSE 내부 클래스 로그는 제외 (FE 피드백 루프 방지)
    if (loggerName.equals(SSE_BROADCASTER_LOGGER) || loggerName.equals(SSE_CONTROLLER_LOGGER)) {
      return;
    }

    // 지연 초기화: Spring Context가 준비된 후 빈 조회
    if (sseLogBroadcaster == null) {
      if (applicationContext == null) {
        return;
      }
      try {
        sseLogBroadcaster = applicationContext.getBean(SseLogBroadcaster.class);
      } catch (Exception e) {
        return;
      }
    }

    DebugLogEvent debugLogEvent = DebugLogEvent.builder()
        .timestamp(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(loggingEvent.getTimeStamp()),
            ZoneId.systemDefault()))
        .level(loggingEvent.getLevel().toString())
        .loggerName(loggingEvent.getLoggerName())
        .message(loggingEvent.getFormattedMessage())
        .threadName(loggingEvent.getThreadName())
        .build();

    sseLogBroadcaster.broadcast(debugLogEvent);
  }
}
