package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.util.LogLineParser;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
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

  private static final int MAX_QUERY_LINE_COUNT = 2000;     // 조회 줄수 상한
  private static final int MAX_GZ_LINE_COUNT = 5000;        // gz 조회 줄수 상한

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

  /**
   * 현재 romrom.log 끝에서부터 최대 requestedLineCount 줄을 읽고,
   * 레벨/키워드 필터를 적용해 시간순(오래된→최신)으로 반환한다.
   * requestedLineCount는 MAX_QUERY_LINE_COUNT로 캡.
   */
  public List<String> readRecentLines(int requestedLineCount, String levelFilter, String keywordFilter) {
    int cappedLineCount = Math.min(Math.max(requestedLineCount, 1), MAX_QUERY_LINE_COUNT);
    if (!currentLogFile.isFile()) {
      log.warn("로그 파일 없음: {}", currentLogFile.getAbsolutePath());
      return new ArrayList<>();
    }
    List<String> tailLines = readTailLines(currentLogFile, cappedLineCount);
    return applyLevelAndKeywordFilter(tailLines, levelFilter, keywordFilter);
  }

  /**
   * 파일 끝에서 최대 4MB 블록만 읽어 UTF-8로 한 번에 디코딩한 뒤 마지막 maxLineCount 줄 반환.
   * 한글(멀티바이트) 안전. 전체 메모리 로드 금지.
   */
  private List<String> readTailLines(File targetFile, int maxLineCount) {
    long maxReadBytes = 4L * 1024 * 1024;
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(targetFile, "r")) {
      long fileLength = randomAccessFile.length();
      long readStart = Math.max(0, fileLength - maxReadBytes);
      int readLength = (int) (fileLength - readStart);
      byte[] tailBytes = new byte[readLength];
      randomAccessFile.seek(readStart);
      randomAccessFile.readFully(tailBytes);
      String tailContent = new String(tailBytes, StandardCharsets.UTF_8);
      String[] allLines = tailContent.split("\n", -1);
      List<String> resultLines = new ArrayList<>();
      int startIndex = Math.max(0, allLines.length - maxLineCount);
      for (int lineIndex = startIndex; lineIndex < allLines.length; lineIndex++) {
        String tailLine = allLines[lineIndex].replace("\r", "");
        if (!tailLine.isEmpty()) {
          resultLines.add(tailLine);
        }
      }
      return resultLines;
    } catch (IOException e) {
      log.error("로그 tail 읽기 실패: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * 레벨/키워드 필터 적용. levelFilter가 null/blank/"전체"면 레벨 무시, keyword가 비면 키워드 무시.
   * 결과는 입력 순서(시간순) 유지.
   */
  private List<String> applyLevelAndKeywordFilter(List<String> rawLines, String levelFilter, String keywordFilter) {
    boolean isLevelActive = levelFilter != null && !levelFilter.isBlank() && !"전체".equals(levelFilter);
    boolean isKeywordActive = keywordFilter != null && !keywordFilter.isBlank();
    List<String> filteredLines = new ArrayList<>();
    for (String rawLine : rawLines) {
      if (isLevelActive) {
        com.romrom.application.dto.LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
        String lineLevel = parsed.getLogLevel();
        if (lineLevel == null || !lineLevel.equalsIgnoreCase(levelFilter)) {
          continue;
        }
      }
      if (isKeywordActive && !rawLine.contains(keywordFilter)) {
        continue;
      }
      filteredLines.add(rawLine);
    }
    return filteredLines;
  }
}
