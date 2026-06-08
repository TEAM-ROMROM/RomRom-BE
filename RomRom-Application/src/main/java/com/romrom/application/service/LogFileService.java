package com.romrom.application.service;

import com.romrom.application.dto.AdminResponse.AdminLogErrorSummary;
import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.dto.LogLineParsed;
import com.romrom.application.util.LogLineParser;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
  private static final Pattern EXCEPTION_CLASS_PATTERN = Pattern.compile("([A-Za-z0-9_.]+Exception)");

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
        LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
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

  /**
   * 최근 withinMinutes 분 내 ERROR/WARN 로그를 예외 클래스별로 집계.
   * 예외명을 못 찾으면 로거명을 키로 사용.
   * sortBy="recent"면 마지막 발생시각 내림차순, 그 외(기본 "count")는 발생횟수 내림차순.
   */
  public List<AdminLogErrorSummary> aggregateErrors(int withinMinutes, String sortBy) {
    List<String> recentLines = readRecentLines(MAX_QUERY_LINE_COUNT, null, null);
    LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(withinMinutes);
    Map<String, AdminLogErrorSummary> summaryByExceptionKey = new LinkedHashMap<>();

    for (String rawLine : recentLines) {
      LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
      String lineLevel = parsed.getLogLevel();
      if (lineLevel == null
          || !(lineLevel.equalsIgnoreCase("ERROR") || lineLevel.equalsIgnoreCase("WARN"))) {
        continue;
      }
      if (parsed.getLoggedAt() != null && parsed.getLoggedAt().isBefore(cutoffTime)) {
        continue;
      }
      String aggregationKey = extractExceptionKey(parsed);
      AdminLogErrorSummary existingSummary = summaryByExceptionKey.get(aggregationKey);
      if (existingSummary == null) {
        summaryByExceptionKey.put(aggregationKey, AdminLogErrorSummary.builder()
            .exceptionClassName(aggregationKey)
            .occurrenceCount(1)
            .lastOccurredAt(parsed.getLoggedAt())
            .representativeMessage(safeShortMessage(parsed.getLogMessage()))
            .build());
      } else {
        existingSummary.setOccurrenceCount(existingSummary.getOccurrenceCount() + 1);
        if (parsed.getLoggedAt() != null
            && (existingSummary.getLastOccurredAt() == null
                || parsed.getLoggedAt().isAfter(existingSummary.getLastOccurredAt()))) {
          existingSummary.setLastOccurredAt(parsed.getLoggedAt());
        }
      }
    }
    List<AdminLogErrorSummary> summaries = new ArrayList<>(summaryByExceptionKey.values());
    if ("recent".equalsIgnoreCase(sortBy)) {
      // 마지막 발생시각 최신순. 최신 시각이 위로 오도록 reverseOrder + null은 항상 맨 뒤.
      summaries.sort(Comparator.comparing(
          AdminLogErrorSummary::getLastOccurredAt,
          Comparator.nullsLast(Comparator.reverseOrder())));
    } else {
      // 기본: 발생횟수 내림차순 (많은순)
      summaries.sort(Comparator.comparing(AdminLogErrorSummary::getOccurrenceCount).reversed());
    }
    return summaries;
  }

  private String extractExceptionKey(LogLineParsed parsed) {
    String logMessage = parsed.getLogMessage();
    if (logMessage != null) {
      Matcher exceptionMatcher = EXCEPTION_CLASS_PATTERN.matcher(logMessage);
      if (exceptionMatcher.find()) {
        return exceptionMatcher.group(1);
      }
    }
    return parsed.getLoggerName() != null ? parsed.getLoggerName() : "UNKNOWN";
  }

  private String safeShortMessage(String logMessage) {
    if (logMessage == null) {
      return "";
    }
    String firstLine = logMessage.split("\n", 2)[0];
    return firstLine.length() > 300 ? firstLine.substring(0, 300) : firstLine;
  }

  /**
   * 지정한 .gz 파일을 압축 해제해 레벨/키워드 필터를 적용한 라인을 반환.
   * fileName은 화이트리스트(listLogFiles) 검증 후에만 처리.
   */
  public List<String> readGzLines(String fileName, int requestedLineCount, String levelFilter, String keywordFilter) {
    File gzFile = resolveWhitelistedFile(fileName);
    int cappedLineCount = Math.min(Math.max(requestedLineCount, 1), MAX_GZ_LINE_COUNT);
    List<String> decompressedLines = new ArrayList<>();
    try (GZIPInputStream gzInputStream = new GZIPInputStream(Files.newInputStream(gzFile.toPath()));
        BufferedReader gzReader = new BufferedReader(
            new InputStreamReader(gzInputStream, StandardCharsets.UTF_8))) {
      String gzLine;
      while ((gzLine = gzReader.readLine()) != null && decompressedLines.size() < cappedLineCount) {
        decompressedLines.add(gzLine);
      }
    } catch (IOException e) {
      log.error("gz 압축 해제 실패 {}: {}", fileName, e.getMessage());
      throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    return applyLevelAndKeywordFilter(decompressedLines, levelFilter, keywordFilter);
  }

  /**
   * 현재 romrom.log에서 최근 range 기간 라인만 잘라 하나의 텍스트로 반환.
   * 범위 내 로그가 없으면 안내 헤더 한 줄.
   */
  public String extractByTimeRange(Duration range) {
    LocalDateTime cutoffTime = LocalDateTime.now().minus(range);
    List<String> tailLines = readRecentLines(MAX_QUERY_LINE_COUNT, null, null);
    StringBuilder extractedBuilder = new StringBuilder();
    for (String rawLine : tailLines) {
      LogLineParsed parsed = logLineParser.parseSingleLine(rawLine);
      if (parsed.getLoggedAt() == null || !parsed.getLoggedAt().isBefore(cutoffTime)) {
        extractedBuilder.append(rawLine).append('\n');
      }
    }
    if (extractedBuilder.length() == 0) {
      return "# 범위 내 로그 없음\n";
    }
    return extractedBuilder.toString();
  }

  /**
   * 화이트리스트 검증된 파일을 다운로드 Resource로 반환.
   */
  public Resource getLogFileResource(String fileName) {
    File targetFile = resolveWhitelistedFile(fileName);
    return new FileSystemResource(targetFile);
  }

  /**
   * fileName이 listLogFiles 화이트리스트에 있고, 로그 디렉터리 하위로만 resolve되는지 검증.
   * 위반 시 CustomException(INVALID_REQUEST).
   */
  private File resolveWhitelistedFile(String fileName) {
    boolean isWhitelisted = listLogFiles().stream()
        .anyMatch(logFileInfo -> logFileInfo.getFileName().equals(fileName));
    if (!isWhitelisted) {
      log.warn("화이트리스트 밖 로그 파일 요청 차단: {}", fileName);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    Path resolvedPath = logDirectory.toPath().resolve(fileName).normalize();
    if (!resolvedPath.startsWith(logDirectory.toPath().normalize())) {
      log.warn("경로 조작 차단: {}", fileName);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return resolvedPath.toFile();
  }

  /**
   * 로그 총 용량 (파일 목록 합산).
   */
  public long getLogTotalSizeBytes(List<AdminLogFileInfo> logFiles) {
    return logFiles.stream().mapToLong(AdminLogFileInfo::getFileSizeBytes).sum();
  }

  public long getDiskFreeBytes() {
    return logDirectory.isDirectory() ? logDirectory.getFreeSpace() : 0L;
  }

  public long getDiskTotalBytes() {
    return logDirectory.isDirectory() ? logDirectory.getTotalSpace() : 0L;
  }
}
