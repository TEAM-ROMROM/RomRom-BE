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
}
