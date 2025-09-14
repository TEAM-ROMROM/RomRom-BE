package com.romrom.web.controller.api;

import com.romrom.web.dto.AdminRequest;
import com.romrom.web.dto.AdminResponse;
import com.romrom.web.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@Slf4j
public class AdminApiController {
    
    private final AdminAuthService adminAuthService;
    
    
    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> login(@ModelAttribute AdminRequest request,
                                               HttpServletResponse response) {
        
        try {
            // JWT 토큰 발급
            AdminResponse loginResponse = adminAuthService.authenticateWithJwt(request.getUsername(), request.getPassword());
            
            // refreshToken을 쿠키에 저장 (httpOnly, secure)
            Cookie refreshTokenCookie = new Cookie("adminRefreshToken", loginResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false); // HTTPS 환경에서는 true로 설정
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
            response.addCookie(refreshTokenCookie);
            
            // JavaScript :로그인 상태 확인용 쿠키 (토큰 값 포함X)
            Cookie authStatusCookie = new Cookie("adminAuthStatus", "authenticated");
            authStatusCookie.setHttpOnly(false); // JavaScript에서 접근 가능
            authStatusCookie.setSecure(false);
            authStatusCookie.setPath("/");
            authStatusCookie.setMaxAge(60 * 60); // 1시간 (accessToken과 동일)
            response.addCookie(authStatusCookie);
            
            log.info("관리자 JWT 로그인 성공: {}", request.getUsername());
            // RefreshToken도 Response Body에 포함 (클라이언트가 선택 가능)
            // 쿠키와 Response Body 둘 다 제공하여 클라이언트가 원하는 방식 사용
            return ResponseEntity.ok(loginResponse);
            
        } catch (Exception e) {
            log.error("관리자 로그인 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PostMapping(value = "/api/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> logout(@CookieValue(value = "adminRefreshToken", required = false) String refreshTokenFromCookie,
                                      HttpServletResponse response) {
        
        // 토큰 무효화 (블랙리스트 등록) - 쿠키에서 refreshToken 가져오기
        if (refreshTokenFromCookie != null) {
            // refreshToken으로 accessToken 추출 후 로그아웃 처리
            adminAuthService.logout(refreshTokenFromCookie);
        }
        
        // 쿠키 삭제 (이제 accessToken 쿠키는 없으므로 제거)
        
        Cookie refreshTokenCookie = new Cookie("adminRefreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
        
        Cookie authStatusCookie = new Cookie("adminAuthStatus", null);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(0);
        response.addCookie(authStatusCookie);
        
        log.info("관리자 로그아웃 완료");
        
        return ResponseEntity.ok().build();
    }
}
