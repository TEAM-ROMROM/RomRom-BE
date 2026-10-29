package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminDashboardService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberQueryService;
import com.romrom.application.service.AdminReportService;
import com.romrom.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 대시보드 API: 통계, 최근 가입 회원·거래·물품, 동접자
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminDashboardController implements AdminDashboardControllerDocs {

    private final AdminItemService adminItemService;
    private final AdminMemberQueryService adminMemberQueryService;
    private final MemberService memberService;
    private final AdminReportService adminReportService;
    private final AdminDashboardService adminDashboardService;

    @Override
    @PostMapping(value = "/dashboard/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDashboardStats(@ModelAttribute AdminRequest request) {
        AdminResponse.AdminDashboardStats tradeStatusStats = adminDashboardService.getTradeStatusStats(request);
        return ResponseEntity.ok(AdminResponse.builder()
            .dashboardStats(AdminResponse.AdminDashboardStats.builder()
                .totalMembers(memberService.countActiveMembers())
                .totalItems(adminDashboardService.countActiveItems())
                .ongoingTrades(adminDashboardService.countOngoingTrades())
                .pendingReports(adminReportService.countPendingReports())
                .tradeStatusCounts(tradeStatusStats.getTradeStatusCounts())
                .newReviewCount(tradeStatusStats.getNewReviewCount())
                .build())
            .build());
    }

    @Override
    @PostMapping(value = "/dashboard/recent-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberQueryService.getRecentMembersForAdmin(8));
    }

    @Override
    @PostMapping(value = "/dashboard/recent-trades", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentTrades(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminDashboardService.getRecentTrades(8));
    }

    @Override
    @PostMapping(value = "/dashboard/recent-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getRecentItemsForAdmin(8));
    }

    @Override
    @PostMapping(value = "/dashboard/online-stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getOnlineStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminDashboardService.getOnlineStats());
    }
}
