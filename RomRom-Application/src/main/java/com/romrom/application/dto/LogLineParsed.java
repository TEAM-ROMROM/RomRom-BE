package com.romrom.application.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 로그 파일 한 줄(멀티라인 스택트레이스 포함)을 파싱한 결과.
 * 타임스탬프로 시작하지 않는 라인은 직전 로그의 message에 결합된다.
 */
@Getter
@Builder
@AllArgsConstructor
public class LogLineParsed {
  private final LocalDateTime loggedAt;   // 파싱 실패 시 null
  private final String logLevel;          // ERROR/WARN/INFO/DEBUG/TRACE, 실패 시 null
  private final String loggerName;        // 실패 시 null
  private final String logMessage;        // 멀티라인 결합 포함
  private final String rawLine;           // 원본 라인 (필터/출력 폴백용)
}
