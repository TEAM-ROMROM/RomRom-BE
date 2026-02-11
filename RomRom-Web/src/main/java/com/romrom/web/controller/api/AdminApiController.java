package com.romrom.web.controller.api;

import com.romrom.auth.service.AdminAuthService;
import com.romrom.common.dto.AdminRequest;
import com.romrom.common.dto.AdminResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminApiController {

    private final AdminAuthService adminAuthService;
    private final ItemService itemService;
    private final MemberService memberService;
    private final AdminReportService adminReportService;
    
    
    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> login(@ModelAttribute AdminRequest request,
                                               HttpServletResponse response) {
        
        try {
            // JWT 토큰 발급
            AdminResponse loginResponse = adminAuthService.authenticateWithJwt(request.getUsername(), request.getPassword());
            
            // accessToken을 쿠키에 저장 (페이지 요청용)
            Cookie accessTokenCookie = new Cookie("accessToken", loginResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true); // XSS 방지
            accessTokenCookie.setSecure(false); // HTTPS 환경에서는 true로 설정
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(60 * 60); // 1시간
            response.addCookie(accessTokenCookie);
            
            // refreshToken을 쿠키에 저장 (httpOnly, secure)
            Cookie refreshTokenCookie = new Cookie("refreshToken", loginResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false); // HTTPS 환경에서는 true로 설정
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
            response.addCookie(refreshTokenCookie);
            
            // JavaScript :로그인 상태 확인용 쿠키 (토큰 값 포함X)
            Cookie authStatusCookie = new Cookie("authStatus", "authenticated");
            authStatusCookie.setHttpOnly(false); // JavaScript에서 접근 가능
            authStatusCookie.setSecure(false);
            authStatusCookie.setPath("/");
            authStatusCookie.setMaxAge(60 * 60); // 1시간 (accessToken과 동일)
            response.addCookie(authStatusCookie);
            
            log.info("관리자 JWT 로그인 성공: {}", request.getUsername());
            return ResponseEntity.ok(loginResponse);
            
        } catch (Exception e) {
            log.error("관리자 로그인 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> logout(@CookieValue(value = "refreshToken", required = false) String refreshTokenFromCookie,
                                      HttpServletResponse response) {
        
        // 토큰 무효화 (블랙리스트 등록) - 쿠키에서 refreshToken 가져오기
        if (refreshTokenFromCookie != null) {
            // refreshToken으로 accessToken 추출 후 로그아웃 처리
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
        return ResponseEntity.ok(memberService.getRecentMembersForAdmin(8));
    }

    @GetMapping("/dashboard/recent-items")
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems() {
        return ResponseEntity.ok(itemService.getRecentItemsForAdmin(8));
    }

    // ==================== Items ====================

    @GetMapping("/items")
    @LogMonitor
    public ResponseEntity<AdminResponse> getItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(itemService.getItemsForAdmin(request));
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
        return ResponseEntity.ok(memberService.getMembersForAdmin(request));
    }

    // ==================== Reports ====================

    @PostMapping("/reports")
    @LogMonitor
    public ResponseEntity<AdminReportResponse> handleReports(@RequestBody AdminReportRequest request) {
        return ResponseEntity.ok(adminReportService.handleAction(request));
    }
}
