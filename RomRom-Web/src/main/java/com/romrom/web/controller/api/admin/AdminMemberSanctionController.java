package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.BulkActionResult;
import com.romrom.application.service.AdminMemberNotificationService;
import com.romrom.application.service.AdminMemberQueryService;
import com.romrom.application.service.AdminMemberSanctionService;
import com.romrom.auth.dto.CustomUserDetails;
import java.util.List;
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
 * 관리자 회원 조치 API: 상태 변경, 정지·해제, 강제탈퇴, 일괄 정지·탈퇴·물품 삭제, 알림 발송, 제재 이력
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminMemberSanctionController implements AdminMemberSanctionControllerDocs {

    private final AdminMemberQueryService adminMemberQueryService;
    private final AdminMemberSanctionService adminMemberSanctionService;
    private final AdminMemberNotificationService adminMemberNotificationService;

    @Override
    @PostMapping(value = "/members/force-withdraw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> forceWithdrawMember(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        adminMemberSanctionService.forceWithdrawMember(
            adminRequest.getMemberId(),
            adminRequest.getForceWithdrawReason(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder().build());
    }

    @Override
    @PostMapping(value = "/members/bulk-suspend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> bulkSuspendMembers(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<BulkActionResult> bulkSuspendResults = adminMemberSanctionService.bulkSuspendMembers(
            adminRequest.getMemberIds(),
            adminRequest.getSuspendReason(),
            adminRequest.getSuspendedUntil(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder()
            .bulkActionResults(bulkSuspendResults)
            .build());
    }

    @Override
    @PostMapping(value = "/members/bulk-withdraw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> bulkWithdrawMembers(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<BulkActionResult> bulkWithdrawResults = adminMemberSanctionService.bulkWithdrawMembers(
            adminRequest.getMemberIds(),
            adminRequest.getForceWithdrawReason(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder()
            .bulkActionResults(bulkWithdrawResults)
            .build());
    }

    @Override
    @PostMapping(value = "/members/items/bulk-delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> bulkDeleteMemberItems(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<BulkActionResult> bulkDeleteResults = adminMemberSanctionService.bulkDeleteItems(
            adminRequest.getMemberId(),
            adminRequest.getItemIds(),
            // 상세 사유(String)를 전달 — bulkDeleteItems 내부에서 사유 분류는 ETC로 고정
            adminRequest.getItemAdminDeleteDetail(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder()
            .bulkActionResults(bulkDeleteResults)
            .build());
    }

    @Override
    @PostMapping(value = "/members/send-notification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> sendNotificationToMember(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        adminMemberNotificationService.sendNotificationToMember(
            adminRequest.getMemberId(),
            adminRequest.getAdminNotificationTitle(),
            adminRequest.getAdminNotificationContent(),
            adminRequest.getAdminNotificationType(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder().build());
    }

    @Override
    @PostMapping(value = "/members/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMemberStatus(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberSanctionService.updateMemberStatusForAdmin(request));
    }

    @Override
    @PostMapping(value = "/members/suspend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> suspendMember(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberSanctionService.suspendMember(request));
    }

    @Override
    @PostMapping(value = "/members/unsuspend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> unsuspendMember(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberSanctionService.unsuspendMember(request));
    }

    @Override
    @PostMapping(value = "/members/sanction-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberSanctionHistory(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberQueryService.getMemberSanctionHistory(request));
    }

    @Override
    @PostMapping(value = "/sanctions/history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAllSanctionHistory(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberQueryService.getAllSanctionHistory(request));
    }
}
