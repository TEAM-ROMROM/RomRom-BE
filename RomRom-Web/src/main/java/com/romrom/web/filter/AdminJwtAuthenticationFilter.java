package com.romrom.web.filter;

import com.romrom.auth.jwt.JwtUtil;
import com.romrom.auth.service.CustomUserDetailsService;
import com.romrom.common.constant.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 관리자 전용 JWT 인증 필터
 */
@RequiredArgsConstructor
@Slf4j
public class AdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 인증이 필요없는 관리자 경로
    private static final List<String> ADMIN_WHITELIST = Arrays.asList(
        "/admin/login",
        "/admin/logout"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        
        // 관리자 경로가 아니면 다음 필터로
        if (!uri.startsWith("/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 화이트리스트 경로면 인증 없이 통과
        if (isWhitelistedPath(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("관리자 페이지 JWT 인증 필터 실행: {}", uri);

        try {
            String accessToken = null;
            
            // 1. 쿠키에서 토큰 확인
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("adminAccessToken".equals(cookie.getName())) {
                        accessToken = cookie.getValue();
                        break;
                    }
                }
            }
            
            // 2. Authorization 헤더에서 토큰 확인 (API 호출 시)
            if (accessToken == null) {
                String bearerToken = request.getHeader("Authorization");
                if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                    accessToken = bearerToken.substring(7).trim();
                }
            }
            
            // 토큰 검증
            if (accessToken != null && jwtUtil.validateToken(accessToken)) {
                Authentication authentication = jwtUtil.getAuthentication(accessToken);
                
                // 관리자 권한 확인
                boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
                
                if (!isAdmin) {
                    log.error("관리자 권한이 없는 토큰: {}", authentication.getName());
                    response.sendRedirect("/admin/login?error=unauthorized");
                    return;
                }
                
                // 인증 정보 설정
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("관리자 인증 성공: {}", authentication.getName());
                
                filterChain.doFilter(request, response);
                return;
            }
            
            // 토큰이 없거나 유효하지 않은 경우
            log.warn("관리자 토큰이 없거나 유효하지 않음");
            response.sendRedirect("/admin/login?error=invalid");
            
        } catch (Exception e) {
            log.error("관리자 인증 필터 에러: {}", e.getMessage());
            response.sendRedirect("/admin/login?error=error");
        }
    }

    private boolean isWhitelistedPath(String uri) {
        return ADMIN_WHITELIST.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}