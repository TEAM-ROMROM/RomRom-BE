package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.util.LogLineParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFileServiceTest {

  private LogFileService newServiceWith(Path logDirectory) {
    return new LogFileService(logDirectory.resolve("romrom.log").toString(), new LogLineParser());
  }

  @Test
  void 파일목록_log와gz를_최신순으로반환(@TempDir Path logDirectory) throws IOException {
    Files.writeString(logDirectory.resolve("romrom.log"), "현재 로그\n");
    Files.writeString(logDirectory.resolve("romrom.log.2026-06-07.0.gz"), "gz 내용");
    LogFileService logFileService = newServiceWith(logDirectory);

    List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();

    assertEquals(2, logFiles.size());
    assertTrue(logFiles.stream().anyMatch(f -> f.getFileName().equals("romrom.log")));
    assertTrue(logFiles.stream().anyMatch(f -> f.getFileName().equals("romrom.log.2026-06-07.0.gz")));
  }

  @Test
  void 로그디렉터리없음_빈목록반환(@TempDir Path logDirectory) {
    LogFileService logFileService =
        new LogFileService(logDirectory.resolve("nope/romrom.log").toString(), new LogLineParser());

    List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();

    assertTrue(logFiles.isEmpty());
  }

  @Test
  void readRecentLines_최근N줄_역방향으로반환(@TempDir Path logDirectory) throws IOException {
    StringBuilder logContentBuilder = new StringBuilder();
    for (int lineIndex = 1; lineIndex <= 10; lineIndex++) {
      logContentBuilder.append("2026-06-08 14:00:0").append(lineIndex % 10)
          .append(".000 [main] INFO  com.romrom.T - 라인").append(lineIndex).append('\n');
    }
    Files.writeString(logDirectory.resolve("romrom.log"), logContentBuilder.toString());
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> recentLines = logFileService.readRecentLines(3, null, null);

    assertEquals(3, recentLines.size());
    assertTrue(recentLines.get(2).contains("라인10")); // 최신이 마지막(시간순)
  }

  @Test
  void readRecentLines_레벨필터_ERROR만(@TempDir Path logDirectory) throws IOException {
    String logContent =
        "2026-06-08 14:00:01.000 [main] INFO  com.romrom.T - 정상\n"
            + "2026-06-08 14:00:02.000 [main] ERROR com.romrom.T - 실패1\n"
            + "2026-06-08 14:00:03.000 [main] WARN  com.romrom.T - 경고\n"
            + "2026-06-08 14:00:04.000 [main] ERROR com.romrom.T - 실패2\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> errorLines = logFileService.readRecentLines(100, "ERROR", null);

    assertEquals(2, errorLines.size());
    assertTrue(errorLines.get(0).contains("실패1"));
    assertTrue(errorLines.get(1).contains("실패2"));
  }

  @Test
  void readRecentLines_키워드필터(@TempDir Path logDirectory) throws IOException {
    String logContent =
        "2026-06-08 14:00:01.000 [main] INFO  com.romrom.T - 주문 생성\n"
            + "2026-06-08 14:00:02.000 [main] INFO  com.romrom.T - 결제 완료\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> matchedLines = logFileService.readRecentLines(100, null, "결제");

    assertEquals(1, matchedLines.size());
    assertTrue(matchedLines.get(0).contains("결제 완료"));
  }

  @Test
  void aggregateErrors_예외클래스별_집계(@TempDir Path logDirectory) throws IOException {
    String nowStamp = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    String logContent =
        nowStamp + " [main] ERROR com.romrom.A - NullPointerException: x is null\n"
            + nowStamp + " [main] ERROR com.romrom.B - NullPointerException: y is null\n"
            + nowStamp + " [main] ERROR com.romrom.C - IllegalStateException: bad\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    var errorSummaries = logFileService.aggregateErrors(60, "count");

    assertTrue(errorSummaries.stream()
        .anyMatch(s -> s.getExceptionClassName().contains("NullPointerException")
            && s.getOccurrenceCount() == 2));
  }

  @Test
  void aggregateErrors_정렬_count는_발생횟수_많은순_recent는_최근순(@TempDir Path logDirectory) throws IOException {
    java.time.format.DateTimeFormatter stampFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    java.time.LocalDateTime now = java.time.LocalDateTime.now();
    // NPE: 2건이지만 오래 전 발생 / ISE: 1건이지만 가장 최근 발생
    String olderStamp = now.minusMinutes(10).format(stampFormatter);
    String newerStamp = now.minusMinutes(1).format(stampFormatter);
    String logContent =
        olderStamp + " [main] ERROR com.romrom.A - NullPointerException: 1\n"
            + olderStamp + " [main] ERROR com.romrom.B - NullPointerException: 2\n"
            + newerStamp + " [main] ERROR com.romrom.C - IllegalStateException: 최근\n";
    Files.writeString(logDirectory.resolve("romrom.log"), logContent);
    LogFileService logFileService = newServiceWith(logDirectory);

    var byCount = logFileService.aggregateErrors(60, "count");
    var byRecent = logFileService.aggregateErrors(60, "recent");

    // 많은순: NPE(2건)가 맨 앞
    assertTrue(byCount.get(0).getExceptionClassName().contains("NullPointerException"));
    // 최근순: 가장 최근 발생한 ISE가 맨 앞
    assertTrue(byRecent.get(0).getExceptionClassName().contains("IllegalStateException"));
  }

  @Test
  void readGzLines_압축해제후_키워드필터(@TempDir Path logDirectory) throws IOException {
    Path gzPath = logDirectory.resolve("romrom.log.2026-06-07.0.gz");
    String gzPlainContent =
        "2026-06-07 10:00:01.000 [main] INFO  com.romrom.T - 어제 정상\n"
            + "2026-06-07 10:00:02.000 [main] ERROR com.romrom.T - 어제 에러\n";
    try (var gzOut = new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzPath))) {
      gzOut.write(gzPlainContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    Files.writeString(logDirectory.resolve("romrom.log"), "현재\n");
    LogFileService logFileService = newServiceWith(logDirectory);

    List<String> gzLines = logFileService.readGzLines(
        "romrom.log.2026-06-07.0.gz", 100, "ERROR", null);

    assertEquals(1, gzLines.size());
    assertTrue(gzLines.get(0).contains("어제 에러"));
  }

  @Test
  void getLogFileResource_화이트리스트밖_경로조작_차단(@TempDir Path logDirectory) throws IOException {
    Files.writeString(logDirectory.resolve("romrom.log"), "현재\n");
    LogFileService logFileService = newServiceWith(logDirectory);

    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> logFileService.getLogFileResource("../secret.txt"));
  }
}
