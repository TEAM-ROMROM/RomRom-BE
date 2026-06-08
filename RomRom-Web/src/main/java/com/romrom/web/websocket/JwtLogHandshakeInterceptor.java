package com.romrom.web.websocket;

import com.romrom.auth.jwt.JwtUtil;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * 관리자 실시간 로그 WebSocket(/ws/admin-logs) 핸드셰이크 인증 인터셉터
 *
 * <p>순수 WebSocket은 STOMP CONNECT 프레임이 없으므로, 최초 HTTP 업그레이드(handshake) 단계에서 인증한다.
 * 세 가지를 모두 통과해야 연결을 수립한다:
 * <ol>
 *   <li>Origin 화이트리스트 검증 — CSWSH(Cross-Site WebSocket Hijacking) 차단</li>
 *   <li>쿠키 accessToken JWT 유효성</li>
 *   <li>ROLE_ADMIN 권한</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtLogHandshakeInterceptor implements HandshakeInterceptor {

  private static final String ACCESS_TOKEN_COOKIE = "accessToken";
  private static final String ADMIN_ROLE = "ROLE_ADMIN";

  // SecurityConfig의 CORS allowedOriginPatterns와 동일한 화이트리스트
  // 와일드카드(*.romrom.xyz)는 suffix 매칭으로 처리
  private static final List<String> ALLOWED_ORIGIN_SUFFIXES = List.of(
      "romrom.xyz",                       // https://romrom.xyz, https://*.romrom.xyz
      "romrom.suhsaechan.kr",             // admin.romrom.suhsaechan.kr 등
      "localhost:8080",
      "localhost:3000"
  );

  private final JwtUtil jwtUtil;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attributes) {

    // ① Origin 검증
    String origin = request.getHeaders().getOrigin();
    if (!isAllowedOrigin(origin)) {
      log.warn("로그 WebSocket 핸드셰이크 거부 — 허용되지 않은 Origin: {}", origin);
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }

    // ② 쿠키에서 accessToken 추출
    String accessToken = extractAccessTokenFromCookie(request);
    if (accessToken == null) {
      log.warn("로그 WebSocket 핸드셰이크 거부 — accessToken 쿠키 없음");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    // ③ JWT 유효성 + ROLE_ADMIN 검증
    try {
      if (!jwtUtil.validateToken(accessToken)) {
        log.warn("로그 WebSocket 핸드셰이크 거부 — 유효하지 않은 토큰");
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
      }
      String role = jwtUtil.getRole(accessToken);
      if (!ADMIN_ROLE.equals(role)) {
        log.warn("로그 WebSocket 핸드셰이크 거부 — 관리자 권한 아님: role={}", role);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
    } catch (Exception e) {
      log.warn("로그 WebSocket 핸드셰이크 거부 — 토큰 검증 예외: {}", e.getMessage());
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    return true;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Exception exception) {
    // no-op
  }

  /**
   * Origin이 화이트리스트 suffix 중 하나로 끝나는지 검증.
   * Origin은 "scheme://host[:port]" 형식이므로 host 부분만 비교한다.
   */
  private boolean isAllowedOrigin(String origin) {
    if (origin == null || origin.isBlank()) {
      // Origin 헤더가 없는 요청(비 브라우저 클라이언트)은 거부 — 관리자 화면은 항상 브라우저
      return false;
    }
    String host = origin.replaceFirst("^https?://", "");
    return ALLOWED_ORIGIN_SUFFIXES.stream().anyMatch(host::endsWith);
  }

  /**
   * 핸드셰이크 HTTP 요청 쿠키에서 accessToken 값 추출
   */
  private String extractAccessTokenFromCookie(ServerHttpRequest request) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return null;
    }
    Cookie[] cookies = servletRequest.getServletRequest().getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
