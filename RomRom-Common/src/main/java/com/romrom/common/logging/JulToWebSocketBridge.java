package com.romrom.common.logging;

import com.romrom.common.dto.DebugLogEvent;
import com.romrom.common.service.LogWebSocketBroadcaster;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.springframework.context.ApplicationContext;

/**
 * suhlogger(@LogMonitor 등)의 JUL(java.util.logging) 로그를 실시간 로그 WebSocket으로 흘려보내는 브리지.
 *
 * <p>suhlogger는 표준 logback이 아니라 자체 JUL 로거(me.suhsaechan.suhlogger)로 콘솔에 직접 출력하므로,
 * logback Appender({@link LogWebSocketAppender})로는 잡히지 않는다. 이 Handler를 해당 JUL 로거에 붙여
 * AOP 상세 로그를 가로채 broadcaster로 전달한다.
 *
 * <p>출처를 {@code source="AOP"}로 표기하여, broadcaster가 "AOP 토글을 켠 구독자에게만" 전송한다.
 * (평소엔 폭주 방지를 위해 전송되지 않음)
 */
public class JulToWebSocketBridge extends Handler {

  private static volatile ApplicationContext applicationContext;
  private volatile LogWebSocketBroadcaster logWebSocketBroadcaster;

  public static void setApplicationContext(ApplicationContext applicationContext) {
    JulToWebSocketBridge.applicationContext = applicationContext;
  }

  @Override
  public void publish(LogRecord logRecord) {
    if (logRecord == null) {
      return;
    }

    // 지연 초기화: Spring Context 준비 후 broadcaster 빈 조회
    if (logWebSocketBroadcaster == null) {
      if (applicationContext == null) {
        return;
      }
      try {
        logWebSocketBroadcaster = applicationContext.getBean(LogWebSocketBroadcaster.class);
      } catch (Exception e) {
        return;
      }
    }

    // 구독자가 없으면 변환 비용도 들이지 않음
    if (logWebSocketBroadcaster.getActiveSubscriberCount() == 0) {
      return;
    }

    String message = logRecord.getMessage();
    if (message == null) {
      return;
    }

    DebugLogEvent aopLogEvent = DebugLogEvent.builder()
        .timestamp(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(logRecord.getMillis()),
            ZoneId.systemDefault()))
        .level(julLevelToName(logRecord))
        .loggerName(logRecord.getLoggerName() != null ? logRecord.getLoggerName() : "suhlogger")
        .message(message)
        .threadName(String.valueOf(logRecord.getLongThreadID()))
        .source(LogWebSocketBroadcaster.SOURCE_AOP) // AOP 출처 — 토글 켠 세션에만 전송됨
        .build();

    logWebSocketBroadcaster.broadcast(aopLogEvent);
  }

  /**
   * JUL Level을 logback 스타일 레벨 문자열로 근사 매핑.
   * suhlogger는 대부분 INFO/FINE로 찍으므로 화면 색상 구분용으로 단순 변환한다.
   */
  private String julLevelToName(LogRecord logRecord) {
    int levelValue = logRecord.getLevel().intValue();
    if (levelValue >= java.util.logging.Level.SEVERE.intValue()) {
      return "ERROR";
    }
    if (levelValue >= java.util.logging.Level.WARNING.intValue()) {
      return "WARN";
    }
    if (levelValue >= java.util.logging.Level.INFO.intValue()) {
      return "INFO";
    }
    return "DEBUG";
  }

  @Override
  public void flush() {
    // 스트리밍 방식이라 버퍼 없음 — no-op
  }

  @Override
  public void close() throws SecurityException {
    // 정리할 리소스 없음 — no-op
  }
}
