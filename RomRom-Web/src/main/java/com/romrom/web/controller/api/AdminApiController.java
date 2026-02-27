package com.romrom.web.controller.api;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminAuthService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberService;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import com.romrom.report.dto.AdminReportRequest;
import com.romrom.report.dto.AdminReportResponse;
import com.romrom.report.service.AdminReportService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
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

    @GetMapping("/dashboard/stats")
    @LogMonitor
    public ResponseEntity<AdminResponse> getDashboardStats() {
        return ResponseEntity.ok(AdminResponse.builder()
            .dashboardStats(AdminResponse.AdminDashboardStats.builder()
                .totalMembers(memberService.countActiveMembers())
                .totalItems(itemService.countActiveItems())
                .build())
            .build());
    }

    @GetMapping("/dashboard/recent-members")
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentMembers() {
        return ResponseEntity.ok(adminMemberService.getRecentMembersForAdmin(8));
    }

    @GetMapping("/dashboard/recent-items")
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems() {
        return ResponseEntity.ok(adminItemService.getRecentItemsForAdmin(8));
    }

    // ==================== Items ====================

    @GetMapping("/items")
    @LogMonitor
    public ResponseEntity<AdminResponse> getItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemsForAdmin(request));
    }

    @DeleteMapping("/items/{itemId}")
    @LogMonitor
    public ResponseEntity<Void> deleteItem(@PathVariable UUID itemId) {
        itemService.deleteItemByAdmin(itemId);
        return ResponseEntity.ok().build();
    }

    // ==================== Members ====================

    @GetMapping("/members")
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

    @PostMapping("/reports")
    @LogMonitor
    public ResponseEntity<AdminReportResponse> handleReports(@RequestBody AdminReportRequest request) {
        return ResponseEntity.ok(adminReportService.handleAction(request));
    }
}
