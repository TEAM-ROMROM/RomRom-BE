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
    if (!loggingEvent.getLoggerName().startsWith(TARGET_LOGGER_PREFIX)) {
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
