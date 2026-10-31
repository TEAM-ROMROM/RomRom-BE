package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminMemberManagementService;
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
 * 관리자 회원 운영 API: 차단 관계 조회·해제, FCM 토큰 조회·삭제, 프로필 수정
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminMemberManagementController implements AdminMemberManagementControllerDocs {

    private final AdminMemberManagementService adminMemberManagementService;

    @Override
    @PostMapping(value = "/members/blocks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberBlocks(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberManagementService.getMemberBlocks(request));
    }

    @Override
    @PostMapping(value = "/members/blocks/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> deleteMemberBlock(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberManagementService.deleteMemberBlock(request));
    }

    @Override
    @PostMapping(value = "/members/fcm-tokens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberFcmTokens(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberManagementService.getMemberFcmTokens(request));
    }

    @Override
    @PostMapping(value = "/members/fcm-tokens/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> deleteFcmToken(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberManagementService.deleteFcmToken(request));
    }

    @Override
    @PostMapping(value = "/members/update-profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMemberProfile(@ModelAttribute AdminRequest request,
        @AuthenticationPrincipal CustomUserDetails principal) {
        request.setExecutorAdminId(principal.getMember().getMemberId());
        return ResponseEntity.ok(adminMemberManagementService.updateMemberProfile(request));
    }
}
