package com.romrom.web.controller.api;

import com.romrom.web.dto.AdminResponse;
import com.romrom.web.service.AdminAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@Slf4j
public class AdminApiController {
    
    private final AdminAuthService adminAuthService;
    
    
    @PostMapping("/login")
    public ResponseEntity<AdminResponse> login(@RequestParam String username,
                                                   @RequestParam String password,
                                                   HttpServletResponse response) {
        
        try {
            // JWT 토큰 발급
            AdminResponse loginResponse = adminAuthService.authenticateWithJwt(username, password);
        
            // accessToken을 쿠키에 저장 (httpOnly, secure)
            Cookie accessTokenCookie = new Cookie("adminAccessToken", loginResponse.getAccessToken());
            accessTokenCookie.setHttpOnly(true);
            accessTokenCookie.setSecure(false); // HTTPS 환경에서는 true로 설정
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(60 * 60); // 1시간
            response.addCookie(accessTokenCookie);
            
            // refreshToken을 쿠키에 저장 (httpOnly, secure)
            Cookie refreshTokenCookie = new Cookie("adminRefreshToken", loginResponse.getRefreshToken());
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(false); // HTTPS 환경에서는 true로 설정
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
            response.addCookie(refreshTokenCookie);
            
            log.info("관리자 JWT 로그인 성공: {}", username);
            return ResponseEntity.ok(loginResponse);
            
        } catch (Exception e) {
            log.error("관리자 로그인 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AdminResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()
            );
        }
    }
    
    @PostMapping("/api/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "adminAccessToken", required = false) String accessToken,
                                      HttpServletResponse response) {
        
        // 토큰 무효화 (블랙리스트 등록)
        if (accessToken != null) {
            adminAuthService.logout(accessToken);
        }
        
        // 쿠키 삭제
        Cookie accessTokenCookie = new Cookie("adminAccessToken", null);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);
        
        Cookie refreshTokenCookie = new Cookie("adminRefreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);
        
        log.info("관리자 로그아웃 완료");
        
        return ResponseEntity.ok().build();
    }
}
