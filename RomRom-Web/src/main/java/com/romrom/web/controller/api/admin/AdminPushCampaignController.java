package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminPushCampaignRequest;
import com.romrom.application.dto.AdminPushCampaignResponse;
import com.romrom.application.service.AdminPushCampaignService;
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
 * 관리자 푸시 캠페인 API (#874): 미리보기, 테스트 발송, 발송·예약, 목록, 상세, 예약 취소
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/push-campaigns")
public class AdminPushCampaignController implements AdminPushCampaignControllerDocs {

  private final AdminPushCampaignService adminPushCampaignService;

  @Override
  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> previewCampaign(@ModelAttribute AdminPushCampaignRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    request.setExecutorAdminId(principal.getMember().getMemberId());
    return ResponseEntity.ok(adminPushCampaignService.previewCampaign(request));
  }

  @Override
  @PostMapping(value = "/test-send", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> sendTestCampaign(@ModelAttribute AdminPushCampaignRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    request.setExecutorAdminId(principal.getMember().getMemberId());
    return ResponseEntity.ok(adminPushCampaignService.sendTestCampaign(request));
  }

  @Override
  @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> createCampaign(@ModelAttribute AdminPushCampaignRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    request.setExecutorAdminId(principal.getMember().getMemberId());
    return ResponseEntity.ok(adminPushCampaignService.createCampaign(request));
  }

  @Override
  @PostMapping(value = "/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> getCampaigns(@ModelAttribute AdminPushCampaignRequest request) {
    return ResponseEntity.ok(adminPushCampaignService.getCampaigns(request));
  }

  @Override
  @PostMapping(value = "/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> getCampaignDetail(@ModelAttribute AdminPushCampaignRequest request) {
    return ResponseEntity.ok(adminPushCampaignService.getCampaignDetail(request));
  }

  @Override
  @PostMapping(value = "/cancel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AdminPushCampaignResponse> cancelCampaign(@ModelAttribute AdminPushCampaignRequest request,
      @AuthenticationPrincipal CustomUserDetails principal) {
    request.setExecutorAdminId(principal.getMember().getMemberId());
    return ResponseEntity.ok(adminPushCampaignService.cancelCampaign(request));
  }
}
