package com.romrom.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 실시간 로그 스트리밍용 로그 이벤트 DTO (WebSocket으로 JSON 직렬화되어 전송)
 */
@Getter
@Builder
@AllArgsConstructor
public class DebugLogEvent {

  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private final LocalDateTime timestamp;

  private final String level;

  private final String loggerName;

  private final String message;

  private final String threadName;

  /**
   * 로그 출처 구분 — 클라이언트가 AOP 상세 로그를 시각적으로 구분/필터링하는 데 사용.
   * - "APP": logback 일반 애플리케이션 로그 (기본)
   * - "AOP": suhlogger(@LogMonitor 등) JUL 상세 로그 — 토글 ON인 구독자에게만 전송됨
   */
  @Builder.Default
  private final String source = "APP";
}
