package com.romrom.web.controller.view;

import com.romrom.application.service.AdminDashboardService;
import com.romrom.application.service.AdminReportService;
import com.romrom.auth.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 관리자 화면 공통 레이아웃 값: 처리 대기 배지 2개 (#827), 로그인 관리자 표시 이름, enum 표시 이름 (#838)
 * 대상: AdminPageController 화면만 (API 응답에는 영향 없음)
 */
@ControllerAdvice(assignableTypes = AdminPageController.class)
@RequiredArgsConstructor
public class AdminLayoutModelAdvice {

  private final AdminReportService adminReportService;
  private final AdminDashboardService adminDashboardService;

  @ModelAttribute
  public void addLayoutAttentionCounts(Model model) {
    // 처리 대기 신고 (물품+회원 PENDING)
    model.addAttribute("sidebarPendingReportCount", adminReportService.countPendingReports());
    // 교환 완료 요청 대기 거래
    model.addAttribute("sidebarCompleteRequestedTradeCount", adminDashboardService.countCompleteRequestedTrades());
  }

  @ModelAttribute
  public void addAdminDisplayValues(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
    // 머리글 관리자 이름 (로그인 아이디)
    if (principal != null && principal.getMember() != null) {
      model.addAttribute("adminDisplayName", principal.getMember().getEmail());
    }
    // 화면 공통 enum 표시 이름 (layout.html > window.AdminLabels)
    model.addAttribute("adminEnumLabels", AdminEnumLabels.getLabelsByEnumName());
  }
}
