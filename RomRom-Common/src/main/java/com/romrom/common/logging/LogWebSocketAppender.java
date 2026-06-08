package com.romrom.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.romrom.common.dto.DebugLogEvent;
import com.romrom.common.service.LogWebSocketBroadcaster;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationContext;

/**
 * Logback 커스텀 Appender — 로그 이벤트를 실시간 로그 WebSocket 구독자에게 발행
 * com.romrom 패키지 로그만 필터링하여 전송한다.
 *
 * <p>로그를 보는 행위(WebSocket 핸들러/broadcaster/로그 조회 서비스)가 다시 로그로 잡혀
 * WebSocket으로 되먹임되는 자기 오염(피드백 루프)을 막기 위해 해당 로거들을 제외한다.
 */
public class LogWebSocketAppender extends AppenderBase<ILoggingEvent> {

  private static final String TARGET_LOGGER_PREFIX = "com.romrom";

  // 실시간 로그 스트리밍 내부 클래스 로그는 전송하지 않음 (피드백 루프 방지 + 거부 로그 노출 차단)
  // - LogWebSocketBroadcaster: 브로드캐스트 동작 자체
  // - LogWebSocketHandler: WebSocket 연결/해제 핸들러 (Web 모듈)
  // - JwtLogHandshakeInterceptor/HmacLogHandshakeInterceptor: 핸드셰이크 거부 warn 로그가
  //   기존 구독자에게 흘러가지 않도록 제외 (무한 루프는 아니나 불필요한 노출 방지)
  // - LogFileService: 로그 조회/집계 동작
  private static final String WS_BROADCASTER_LOGGER = "com.romrom.common.service.LogWebSocketBroadcaster";
  private static final String WS_HANDLER_LOGGER = "com.romrom.web.websocket.LogWebSocketHandler";
  private static final String WS_JWT_INTERCEPTOR_LOGGER = "com.romrom.web.websocket.JwtLogHandshakeInterceptor";
  private static final String WS_HMAC_INTERCEPTOR_LOGGER = "com.romrom.web.websocket.HmacLogHandshakeInterceptor";
  private static final String LOG_FILE_SERVICE_LOGGER = "com.romrom.application.service.LogFileService";

  private static volatile ApplicationContext applicationContext;
  private volatile LogWebSocketBroadcaster logWebSocketBroadcaster;

  /**
   * Spring ApplicationContext 설정 (초기화 시 호출)
   */
  public static void setApplicationContext(ApplicationContext applicationContext) {
    LogWebSocketAppender.applicationContext = applicationContext;
  }

  @Override
  protected void append(ILoggingEvent loggingEvent) {
    // com.romrom 패키지 로그만 처리
    String loggerName = loggingEvent.getLoggerName();
    if (!loggerName.startsWith(TARGET_LOGGER_PREFIX)) {
      return;
    }

    // 실시간 로그 스트리밍 내부 클래스 로그는 제외 (피드백 루프 방지)
    if (loggerName.equals(WS_BROADCASTER_LOGGER)
        || loggerName.equals(WS_HANDLER_LOGGER)
        || loggerName.equals(WS_JWT_INTERCEPTOR_LOGGER)
        || loggerName.equals(WS_HMAC_INTERCEPTOR_LOGGER)
        || loggerName.equals(LOG_FILE_SERVICE_LOGGER)) {
      return;
    }

    // 지연 초기화: Spring Context가 준비된 후 빈 조회
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

    DebugLogEvent debugLogEvent = DebugLogEvent.builder()
        .timestamp(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(loggingEvent.getTimeStamp()),
            ZoneId.systemDefault()))
        .level(loggingEvent.getLevel().toString())
        .loggerName(loggingEvent.getLoggerName())
        .message(loggingEvent.getFormattedMessage())
        .threadName(loggingEvent.getThreadName())
        .build();

    logWebSocketBroadcaster.broadcast(debugLogEvent);
  }
}
