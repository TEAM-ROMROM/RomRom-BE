package com.romrom.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * SSE 로그 스트리밍용 로그 이벤트 DTO
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
