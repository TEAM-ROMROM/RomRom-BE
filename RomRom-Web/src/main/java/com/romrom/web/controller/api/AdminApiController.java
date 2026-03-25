package com.romrom.web.controller.api;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminAuthService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberService;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import com.romrom.application.service.AdminAlertConfigService;
import com.romrom.application.service.AdminAnnouncementService;
import com.romrom.application.service.AdminReportService;
import com.romrom.application.service.SystemConfigService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminApiController {

    private final AdminAuthService adminAuthService;
    private final AdminItemService adminItemService;
    private final AdminMemberService adminMemberService;
    private final ItemService itemService;
    private final MemberService memberService;
    private final AdminReportService adminReportService;
    private final AdminAnnouncementService adminAnnouncementService;
    private final SystemConfigService systemConfigService;
    private final AdminAlertConfigService adminAlertConfigService;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> login(@ModelAttribute AdminRequest request,
                                               HttpServletResponse response) {
        AdminResponse loginResponse = adminAuthService.authenticateWithJwt(request.getUsername(), request.getPassword());

        // accessToken 쿠키 (httpOnly - XSS 방지)
        Cookie accessTokenCookie = new Cookie("accessToken", loginResponse.getAccessToken());
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(sslEnabled);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(60 * 60); // 1시간
        response.addCookie(accessTokenCookie);

        // refreshToken 쿠키 (httpOnly - XSS 방지)
        Cookie refreshTokenCookie = new Cookie("refreshToken", loginResponse.getRefreshToken());
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(sslEnabled);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
        response.addCookie(refreshTokenCookie);

        // JavaScript 로그인 상태 확인용 쿠키 (토큰 값 포함X)
        Cookie authStatusCookie = new Cookie("authStatus", "authenticated");
        authStatusCookie.setHttpOnly(false);
        authStatusCookie.setSecure(sslEnabled);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(60 * 60); // accessToken과 동일
        response.addCookie(authStatusCookie);

        log.info("관리자 로그인 성공: {}", request.getUsername());
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> logout(@CookieValue(value = "refreshToken", required = false) String refreshTokenFromCookie,
                                      HttpServletResponse response) {
        if (refreshTokenFromCookie != null) {
            adminAuthService.logout(refreshTokenFromCookie);
        }

        // 쿠키 삭제
        Cookie accessTokenCookie = new Cookie("accessToken", null);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);

        Cookie authStatusCookie = new Cookie("authStatus", null);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(0);
        response.addCookie(authStatusCookie);

        log.info("관리자 로그아웃 완료");
        return ResponseEntity.ok().build();
    }

    // ==================== Dashboard ====================

    @PostMapping(value = "/dashboard/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDashboardStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(AdminResponse.builder()
            .dashboardStats(AdminResponse.AdminDashboardStats.builder()
                .totalMembers(memberService.countActiveMembers())
                .totalItems(itemService.countActiveItems())
                .build())
            .build());
    }

    @PostMapping(value = "/dashboard/recent-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getRecentMembersForAdmin(8));
    }

    @PostMapping(value = "/dashboard/recent-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getRecentItemsForAdmin(8));
    }

    // ==================== Items ====================

    @PostMapping(value = "/items/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemsForAdmin(request));
    }

    @PostMapping(value = "/items/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> deleteItem(@ModelAttribute AdminRequest request) {
        itemService.deleteItemByAdmin(request.getItemId());
        return ResponseEntity.ok().build();
    }

    // ==================== Members ====================

    @PostMapping(value = "/members/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMembersForAdmin(request));
    }

    @PostMapping(value = "/members/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMemberDetailForAdmin(request));
    }

    @PostMapping(value = "/members/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMemberStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.updateMemberStatusForAdmin(request));
    }

    // ==================== Reports ====================

    @PostMapping(value = "/reports/item-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReports(request));
    }

    @PostMapping(value = "/reports/member-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReports(request));
    }

    @PostMapping(value = "/reports/item-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReportDetail(request));
    }

    @PostMapping(value = "/reports/member-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReportDetail(request));
    }

    @PostMapping(value = "/reports/update-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateReportStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.updateStatus(request));
    }

    @PostMapping(value = "/reports/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getReportStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getStats());
    }

    // ==================== Announcements ====================

    @PostMapping(value = "/announcements/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAnnouncements(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.getAnnouncements(request));
    }

    @PostMapping(value = "/announcements/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> createAnnouncement(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.createAnnouncement(request));
    }

    @PostMapping(value = "/announcements/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> deleteAnnouncement(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.deleteAnnouncement(request));
    }

    // ==================== Config ====================

    @PostMapping(value = "/config/ai/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAiConfig());
    }

    @PostMapping(value = "/config/ai/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAiConfig(request));
    }

    @PostMapping(value = "/config/cache/reload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> reloadCache(@ModelAttribute AdminRequest request) {
        systemConfigService.reloadCache();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/config/app-version/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAppVersionConfig());
    }

    @PostMapping(value = "/config/app-version/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAppVersionConfig(request));
    }

    @PostMapping(value = "/config/ugc-filter/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getUgcFilterConfig());
    }

    @PostMapping(value = "/config/ugc-filter/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateUgcFilterConfig(request));
    }

    // ==================== Alert Config ====================

    @PostMapping(value = "/alert-config/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.getAlertConfig());
    }

    @PostMapping(value = "/alert-config/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.updateAlertConfig(request));
    }
}
