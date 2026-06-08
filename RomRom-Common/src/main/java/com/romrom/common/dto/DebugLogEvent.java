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
}
