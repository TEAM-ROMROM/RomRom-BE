package com.romrom.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.application.dto.LogLineParsed;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogLineParserTest {

  private final LogLineParser logLineParser = new LogLineParser();

  @Test
  void 단일라인_정상포맷_파싱() {
    String rawLogLine = "2026-06-08 14:23:01.123 [http-nio-8080-exec-1] ERROR com.romrom.web.X - 무언가 터졌다";

    LogLineParsed parsed = logLineParser.parseSingleLine(rawLogLine);

    assertEquals(LocalDateTime.of(2026, 6, 8, 14, 23, 1, 123_000_000), parsed.getLoggedAt());
    assertEquals("ERROR", parsed.getLogLevel());
    assertEquals("com.romrom.web.X", parsed.getLoggerName());
    assertEquals("무언가 터졌다", parsed.getLogMessage());
  }

  @Test
  void 타임스탬프없는라인_파싱시_레벨null() {
    String stackTraceLine = "\tat com.romrom.web.X.method(X.java:42)";

    LogLineParsed parsed = logLineParser.parseSingleLine(stackTraceLine);

    assertNull(parsed.getLoggedAt());
    assertNull(parsed.getLogLevel());
    assertEquals(stackTraceLine, parsed.getRawLine());
  }

  @Test
  void 멀티라인_스택트레이스_직전로그에_결합() {
    List<String> rawLines = List.of(
        "2026-06-08 14:23:01.123 [main] ERROR com.romrom.A - NPE 발생",
        "java.lang.NullPointerException: null",
        "\tat com.romrom.A.run(A.java:10)",
        "2026-06-08 14:23:02.000 [main] INFO  com.romrom.B - 정상 처리");

    List<LogLineParsed> parsedList = logLineParser.parseLines(rawLines);

    assertEquals(2, parsedList.size());
    assertEquals("ERROR", parsedList.get(0).getLogLevel());
    assertTrue(parsedList.get(0).getLogMessage().contains("NullPointerException"));
    assertTrue(parsedList.get(0).getLogMessage().contains("at com.romrom.A.run"));
    assertEquals("INFO", parsedList.get(1).getLogLevel());
  }
}
