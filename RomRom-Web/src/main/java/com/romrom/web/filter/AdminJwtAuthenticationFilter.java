package com.romrom.web.filter;

import com.romrom.auth.jwt.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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

        // API 요청인지 페이지 요청인지 구분
        boolean isApiRequest = uri.startsWith("/admin/api") || 
                              "XMLHttpRequest".equals(request.getHeader("X-Requested-With")) ||
                              (request.getHeader("Accept") != null && 
                               request.getHeader("Accept").contains("application/json"));

        try {
            String accessToken = null;
            
            // 1. Authorization 헤더에서 accessToken 확인 (API 요청용)
            String bearerToken = request.getHeader("Authorization");
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                accessToken = bearerToken.substring(7).trim();
            }
            
            // 2. 쿠키에서 accessToken 확인 (페이지 요청용)
            if (accessToken == null) {
                jakarta.servlet.http.Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (jakarta.servlet.http.Cookie cookie : cookies) {
                        if ("accessToken".equals(cookie.getName())) {
                            accessToken = cookie.getValue();
                            break;
                        }
                    }
                }
            }
            
            // 토큰 검증
            if (accessToken != null) {
                try {
                    if (!jwtUtil.validateToken(accessToken)) {
                        log.warn("관리자 토큰 유효성 실패");
                        handleUnauthorized(response, isApiRequest);
                        return;
                    }
                } catch (ExpiredJwtException eje) {
                    log.warn("관리자 토큰 만료");
                    handleExpired(response, isApiRequest);
                    return;
                }
                
                Authentication authentication = jwtUtil.getAuthentication(accessToken);
                
                // 관리자 권한 확인
                boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
                
                if (!isAdmin) {
                    log.error("관리자 권한이 없는 토큰: {}", authentication.getName());
                    handleUnauthorized(response, isApiRequest);
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
            handleUnauthorized(response, isApiRequest);
            return;
            
        } catch (Exception e) {
            log.error("관리자 인증 필터 에러: {}", e.getMessage());
            handleAuthenticationError(response, isApiRequest);
        }
    }

    private void handleUnauthorized(HttpServletResponse response, boolean isApiRequest) throws IOException {
        if (response.isCommitted()) {
            log.warn("응답이 이미 커밋되어 권한 에러 처리를 건너뜁니다.");
            return;
        }
        
        if (isApiRequest) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"invalid_token\",\"message\":\"유효하지 않은 토큰입니다\"}");
        } else {
            response.sendRedirect("/admin/login?error=invalid");
        }
    }

    private void handleExpired(HttpServletResponse response, boolean isApiRequest) throws IOException {
        if (response.isCommitted()) {
            log.warn("응답이 이미 커밋되어 만료 토큰 에러 처리를 건너뜁니다.");
            return;
        }
        
        if (isApiRequest) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"expired_token\",\"message\":\"만료된 토큰입니다\"}");
        } else {
            response.sendRedirect("/admin/login?error=expired");
        }
    }

    private void handleAuthenticationError(HttpServletResponse response, boolean isApiRequest) throws IOException {
        // 응답이 이미 커밋된 경우 처리하지 않음
        if (response.isCommitted()) {
            log.warn("응답이 이미 커밋되어 에러 처리를 건너뜁니다.");
            return;
        }
        
        if (isApiRequest) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"authentication_error\",\"message\":\"인증 처리 중 오류가 발생했습니다\"}");
        } else {
            response.sendRedirect("/admin/login?error=error");
        }
    }

    private boolean isWhitelistedPath(String uri) {
        return ADMIN_WHITELIST.stream()
            .anyMatch(pattern -> pathMatcher.match(pattern, uri));
    }
}