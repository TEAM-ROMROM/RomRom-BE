package com.romrom.application.util;

import com.romrom.application.dto.LogLineParsed;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 서버 로그 파일을 라인 단위로 파싱한다.
 *
 * <p>로그 포맷(logback-spring.xml):
 * {@code %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n}
 *
 * <p>타임스탬프로 시작하지 않는 라인(멀티라인 스택트레이스)은 직전 로그의 message에 결합된다.
 */
@Component
public class LogLineParser {

  // group1=timestamp, group2=thread, group3=level, group4=logger, group5=message
  // %-5level 패딩 공백을 흡수하기 위해 level 뒤에 \\s+ 사용
  private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
      "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[(.*?)] (\\w+)\\s+(\\S+) - (.*)$");

  private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  /**
   * 단일 라인을 파싱한다. 헤더 패턴에 매칭되지 않으면 rawLine/logMessage만 채운 결과를 반환한다.
   */
  public LogLineParsed parseSingleLine(String rawLogLine) {
    Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(rawLogLine);
    if (!logLineMatcher.matches()) {
      return LogLineParsed.builder()
          .rawLine(rawLogLine)
          .logMessage(rawLogLine)
          .build();
    }
    return LogLineParsed.builder()
        .loggedAt(LocalDateTime.parse(logLineMatcher.group(1), LOG_TIMESTAMP_FORMATTER))
        .logLevel(logLineMatcher.group(3))
        .loggerName(logLineMatcher.group(4))
        .logMessage(logLineMatcher.group(5))
        .rawLine(rawLogLine)
        .build();
  }

  /**
   * 여러 라인을 파싱한다. 헤더 라인을 만나면 이전 엔트리를 flush하고 새 엔트리를 시작하며,
   * 헤더가 아닌 라인(스택트레이스 연속)은 직전 엔트리의 message에 줄바꿈과 함께 결합한다.
   */
  public List<LogLineParsed> parseLines(List<String> rawLines) {
    List<LogLineParsed> parsedList = new ArrayList<>();

    LogLineParsed pendingHeader = null;
    StringBuilder pendingMessageBuilder = null;
    StringBuilder pendingRawBuilder = null;

    for (String rawLine : rawLines) {
      Matcher logLineMatcher = LOG_LINE_PATTERN.matcher(rawLine);
      boolean isHeaderLine = logLineMatcher.matches();

      if (isHeaderLine) {
        flushPendingEntry(parsedList, pendingHeader, pendingMessageBuilder, pendingRawBuilder);

        pendingHeader = LogLineParsed.builder()
            .loggedAt(LocalDateTime.parse(logLineMatcher.group(1), LOG_TIMESTAMP_FORMATTER))
            .logLevel(logLineMatcher.group(3))
            .loggerName(logLineMatcher.group(4))
            .build();
        pendingMessageBuilder = new StringBuilder(logLineMatcher.group(5));
        pendingRawBuilder = new StringBuilder(rawLine);
        continue;
      }

      // 헤더가 아닌 라인
      if (pendingMessageBuilder == null) {
        // 파일 첫 부분이 헤더 없이 시작하는 경우: 독립 raw 엔트리로 추가
        parsedList.add(parseSingleLine(rawLine));
        continue;
      }
      pendingMessageBuilder.append('\n').append(rawLine);
      pendingRawBuilder.append('\n').append(rawLine);
    }

    flushPendingEntry(parsedList, pendingHeader, pendingMessageBuilder, pendingRawBuilder);
    return parsedList;
  }

  private void flushPendingEntry(
      List<LogLineParsed> parsedList,
      LogLineParsed pendingHeader,
      StringBuilder pendingMessageBuilder,
      StringBuilder pendingRawBuilder) {
    if (pendingHeader == null) {
      return;
    }
    parsedList.add(LogLineParsed.builder()
        .loggedAt(pendingHeader.getLoggedAt())
        .logLevel(pendingHeader.getLogLevel())
        .loggerName(pendingHeader.getLoggerName())
        .logMessage(pendingMessageBuilder.toString())
        .rawLine(pendingRawBuilder.toString())
        .build());
  }
}
