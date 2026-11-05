package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminReviewService;
import com.romrom.common.annotation.AdminAudit;
import com.romrom.common.constant.AdminAuditAction;
import com.romrom.common.constant.AdminAuditTargetType;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 거래 후기 API: 목록, 블라인드·해제
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminReviewController implements AdminReviewControllerDocs {

    private final AdminReviewService adminReviewService;

    @Override
    @PostMapping(value = "/reviews/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getReviews(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.getReviewsForAdmin(request));
    }

    @Override
    @PostMapping(value = "/reviews/blind", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.REVIEW_BLIND, targetType = AdminAuditTargetType.REVIEW, targetIdParameter = "tradeReviewId")
    @LogMonitor
    public ResponseEntity<AdminResponse> blindReview(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.blindReview(request));
    }

    @Override
    @PostMapping(value = "/reviews/unblind", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminAudit(action = AdminAuditAction.REVIEW_UNBLIND, targetType = AdminAuditTargetType.REVIEW, targetIdParameter = "tradeReviewId")
    @LogMonitor
    public ResponseEntity<AdminResponse> unblindReview(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.unblindReview(request));
    }
}
