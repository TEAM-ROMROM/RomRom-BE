package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.service.AdminCsvExportService;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 CSV 내보내기 API (#874): 회원, 물품, 신고 3종 (목록 필터 그대로, 최대 10,000행)
 * 브라우저 다운로드용 GET + 쿼리 파라미터 (logs/download 와 같은 예외), 응답 본문이 커서 @LogMonitor 미적용
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminCsvExportController implements AdminCsvExportControllerDocs {

  private final AdminCsvExportService adminCsvExportService;

  @Override
  @GetMapping("/members/export-csv")
  @AdminAudit(action = AdminAuditAction.CSV_EXPORT_MEMBERS, targetType = AdminAuditTargetType.MEMBER)
  public void exportMembersCsv(@ModelAttribute AdminRequest request, HttpServletResponse response) throws IOException {
    adminCsvExportService.exportMembers(request, response);
  }

  @Override
  @GetMapping("/items/export-csv")
  @AdminAudit(action = AdminAuditAction.CSV_EXPORT_ITEMS, targetType = AdminAuditTargetType.ITEM)
  public void exportItemsCsv(@ModelAttribute AdminRequest request, HttpServletResponse response) throws IOException {
    adminCsvExportService.exportItems(request, response);
  }

  @Override
  @GetMapping("/reports/item-export-csv")
  @AdminAudit(action = AdminAuditAction.CSV_EXPORT_ITEM_REPORTS, targetType = AdminAuditTargetType.REPORT)
  public void exportItemReportsCsv(@ModelAttribute AdminRequest request, HttpServletResponse response) throws IOException {
    adminCsvExportService.exportItemReports(request, response);
  }

  @Override
  @GetMapping("/reports/member-export-csv")
  @AdminAudit(action = AdminAuditAction.CSV_EXPORT_MEMBER_REPORTS, targetType = AdminAuditTargetType.REPORT)
  public void exportMemberReportsCsv(@ModelAttribute AdminRequest request, HttpServletResponse response) throws IOException {
    adminCsvExportService.exportMemberReports(request, response);
  }

  @Override
  @GetMapping("/reports/chat-message-export-csv")
  @AdminAudit(action = AdminAuditAction.CSV_EXPORT_CHAT_MESSAGE_REPORTS, targetType = AdminAuditTargetType.REPORT)
  public void exportChatMessageReportsCsv(@ModelAttribute AdminRequest request, HttpServletResponse response)
      throws IOException {
    adminCsvExportService.exportChatMessageReports(request, response);
  }
}
