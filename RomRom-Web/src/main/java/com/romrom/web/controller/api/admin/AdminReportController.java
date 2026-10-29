package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminReportService;
import com.romrom.auth.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 신고 API: 물품·회원 신고 목록·상세, 상태 변경, 원스톱 처리, 통계
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminReportController implements AdminReportControllerDocs {

    private final AdminReportService adminReportService;

    @Override
    @PostMapping(value = "/reports/item-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReports(request));
    }

    @Override
    @PostMapping(value = "/reports/member-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReports(request));
    }

    @Override
    @PostMapping(value = "/reports/item-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReportDetail(request));
    }

    @Override
    @PostMapping(value = "/reports/member-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReportDetail(request));
    }

    @Override
    @PostMapping(value = "/reports/update-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateReportStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.updateStatus(request));
    }

    @Override
    @PostMapping(value = "/reports/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> resolveReport(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminReportService.resolveReport(request));
    }

    @Override
    @PostMapping(value = "/reports/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getReportStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getStats());
    }
}
