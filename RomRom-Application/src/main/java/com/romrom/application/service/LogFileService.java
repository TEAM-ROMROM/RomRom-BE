package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.util.LogLineParser;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 서버 로그 파일(romrom.log + .gz 롤링)을 읽어 관리자 화면에 제공한다.
 * DB 적재 없이 파일을 직접 읽는다.
 */
@Slf4j
@Service
public class LogFileService {

  private final File currentLogFile;
  private final File logDirectory;
  private final LogLineParser logLineParser;

  public LogFileService(
      @Value("${logging.file.name:romrom.log}") String currentLogFilePath,
      LogLineParser logLineParser) {
    this.currentLogFile = new File(currentLogFilePath);
    this.logDirectory = currentLogFile.getParentFile() != null
        ? currentLogFile.getParentFile()
        : new File(".");
    this.logLineParser = logLineParser;
  }

  /**
   * 로그 디렉터리의 romrom.log + romrom.log.*.gz 목록을 최신 수정순으로 반환.
   * 디렉터리가 없으면 빈 목록.
   */
  public List<AdminLogFileInfo> listLogFiles() {
    if (!logDirectory.isDirectory()) {
      log.warn("로그 디렉터리 없음: {}", logDirectory.getAbsolutePath());
      return new ArrayList<>();
    }
    String currentLogFileName = currentLogFile.getName();
    File[] logFileArray = logDirectory.listFiles((dir, fileName) ->
        fileName.equals(currentLogFileName)
            || (fileName.startsWith(currentLogFileName + ".") && fileName.endsWith(".gz")));
    if (logFileArray == null) {
      return new ArrayList<>();
    }
    List<AdminLogFileInfo> logFileInfoList = new ArrayList<>();
    for (File logFile : logFileArray) {
      logFileInfoList.add(AdminLogFileInfo.builder()
          .fileName(logFile.getName())
          .fileSizeBytes(logFile.length())
          .lastModifiedAt(LocalDateTime.ofInstant(
              Instant.ofEpochMilli(logFile.lastModified()), ZoneId.systemDefault()))
          .build());
    }
    logFileInfoList.sort(Comparator.comparing(AdminLogFileInfo::getLastModifiedAt).reversed());
    return logFileInfoList;
  }
}
