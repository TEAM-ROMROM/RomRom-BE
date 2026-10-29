package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.service.LogFileService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 로그 API: 조회, 에러 집계, 파일 목록, gz 조회, 다운로드
 * 실시간 로그 스트림은 WebSocket(/ws/admin-logs, com.romrom.web.websocket 패키지) 담당 (#788)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminLogController implements AdminLogControllerDocs {

    private final LogFileService logFileService;

    @Override
    @PostMapping(value = "/logs/query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryLogs(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 200;
      List<String> logLines = logFileService.readRecentLines(
          lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(logLines).build());
    }

    @Override
    @PostMapping(value = "/logs/errors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> aggregateLogErrors(@ModelAttribute AdminRequest request) {
      int withinMinutes = request.getLogErrorWithinMinutes() != null ? request.getLogErrorWithinMinutes() : 60;
      String errorSortBy = request.getLogErrorSortBy() != null ? request.getLogErrorSortBy() : "count";
      return ResponseEntity.ok(AdminResponse.builder()
          .logErrorSummaries(logFileService.aggregateErrors(withinMinutes, errorSortBy))
          .build());
    }

    @Override
    @PostMapping(value = "/logs/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> listLogFiles(@ModelAttribute AdminRequest request) {
      List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();
      return ResponseEntity.ok(AdminResponse.builder()
          .logFiles(logFiles)
          .logFileCount(logFiles.size())
          .logTotalSizeBytes(logFileService.getLogTotalSizeBytes(logFiles))
          .diskFreeBytes(logFileService.getDiskFreeBytes())
          .diskTotalBytes(logFileService.getDiskTotalBytes())
          .build());
    }

    @Override
    @PostMapping(value = "/logs/gz-query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryGzLog(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 500;
      List<String> gzLines = logFileService.readGzLines(
          request.getLogFileName(), lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(gzLines).build());
    }

    @Override
    @GetMapping(value = "/logs/download")
    @LogMonitor
    public ResponseEntity<byte[]> downloadByTimeRange(@RequestParam("range") String range) {
      Duration duration = switch (range) {
        case "5m" -> Duration.ofMinutes(5);
        case "1h" -> Duration.ofHours(1);
        case "6h" -> Duration.ofHours(6);
        case "24h" -> Duration.ofHours(24);
        default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
      };
      String extractedContent = logFileService.extractByTimeRange(duration);
      String serverStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      String downloadFileName = "romrom-log_" + range + "_" + serverStamp + ".log";
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
          .body(extractedContent.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @GetMapping(value = "/logs/download-file")
    @LogMonitor
    public ResponseEntity<Resource> downloadLogFile(@RequestParam("fileName") String fileName) {
      Resource logFileResource = logFileService.getLogFileResource(fileName);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
          .body(logFileResource);
    }
}
