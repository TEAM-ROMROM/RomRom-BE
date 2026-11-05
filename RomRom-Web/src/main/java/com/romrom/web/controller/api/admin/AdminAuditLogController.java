package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminAuditLogRequest;
import com.romrom.application.dto.AdminAuditLogResponse;
import com.romrom.application.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 작업 기록 API (#874): 목록 (기간·관리자·작업·대상 필터, 최근순 페이지)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController implements AdminAuditLogControllerDocs {

  private final AdminAuditLogService adminAuditLogService;

  @Override
  @PostMapping(value = "/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminAuditLogResponse> getAuditLogs(@ModelAttribute AdminAuditLogRequest request) {
    return ResponseEntity.ok(adminAuditLogService.getAuditLogs(request));
  }
}
