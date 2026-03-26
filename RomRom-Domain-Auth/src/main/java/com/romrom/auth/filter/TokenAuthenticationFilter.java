package com.romrom.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.dto.SecurityUrls;
import com.romrom.common.exception.SuspendedMemberResponse;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.exception.ErrorResponse;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 토큰 기반 인증 필터
 */
@RequiredArgsConstructor
@Slf4j
public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String uri = request.getRequestURI();
    log.debug("요청된 URI: {}", uri);

    // 화이트리스트 체크 : 화이트리스트 경로면 필터링 건너뜀
    if (isWhitelistedPath(uri)) {
      filterChain.doFilter(request, response);
      return;
    }

    // 관리자 페이지 요청은 AdminJwtAuthenticationFilter에서 처리하도록 넘김
    if (uri.startsWith("/admin/") || uri.startsWith("/api/admin/")) {
      filterChain.doFilter(request, response);
      return;
    }

    // 요청 타입 구분 : API 요청만 처리
    boolean isApiRequest = uri.startsWith("/api/");

    try {
      String token = null;
      // API 요청 : Authorization 헤더에서 "Bearer " 토큰 추출
      if (isApiRequest) {
        log.debug("일반 API 요청입니다.");
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
          token = bearerToken.substring(7).trim();
        }
      }

      // 토큰 검증: 토큰이 유효하면 인증 설정
      if (token != null && jwtUtil.validateToken(token)) {
        Authentication authentication = jwtUtil.getAuthentication(token);

        // 제재 상태 체크: SUSPENDED_ACCOUNT면 403 응답 반환
        if (authentication.getPrincipal() instanceof CustomUserDetails suspendedUserDetails) {
          AccountStatus memberAccountStatus = suspendedUserDetails.getMember().getAccountStatus();
          if (memberAccountStatus == AccountStatus.SUSPENDED_ACCOUNT) {
            log.warn("제재된 회원의 API 요청 차단. memberId: {}", suspendedUserDetails.getMemberId());
            sendSuspendedResponse(response,
                suspendedUserDetails.getMember().getSuspendReason(),
                suspendedUserDetails.getMember().getSuspendedUntil());
            return;
          }
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 인증 성공
        filterChain.doFilter(request, response);
        return;
      }

      // 토큰이 없거나 유효하지 않은 경우
      if (isApiRequest) {
        // 토큰 없음
        if (token == null) {
          log.error("토큰이 존재하지 않습니다.");
          sendErrorResponse(response, ErrorCode.MISSING_AUTH_TOKEN);
        } else { // 유효하지 않은 토큰
          log.error("토큰이 유효하지 않습니다.");
          sendErrorResponse(response, ErrorCode.INVALID_ACCESS_TOKEN);
        }
        return; // 필터 체인 진행하지 않음
      }
    } catch (ExpiredJwtException e) {
      log.error("토큰 만료: {}", e.getMessage());
      // 토큰 만료 예외 처리
      sendErrorResponse(response, ErrorCode.EXPIRED_ACCESS_TOKEN);
      return;
    }

    // 필터 체인 계속 진행
    filterChain.doFilter(request, response);
  }

  /**
   * 에러 응답을 JSON 형태로 클라이언트에 전송
   *
   * @param response  HttpServletResponse 객체
   * @param errorCode 발생한 에러코드
   * @throws IOException
   */
  private void sendErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(errorCode.getStatus().value());
    response.setCharacterEncoding("UTF-8");

    ErrorResponse errorResponse = ErrorResponse.builder()
        .errorCode(errorCode)
        .errorMessage(errorCode.getMessage())
        .build();

    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValue(response.getWriter(), errorResponse);
  }

  /**
   * 제재된 회원에게 403 응답을 JSON 형태로 전송
   *
   * @param response       HttpServletResponse 객체
   * @param suspendReason  제재 사유
   * @param suspendedUntil 제재 해제 예정일
   * @throws IOException
   */
  private void sendSuspendedResponse(HttpServletResponse response,
      String suspendReason,
      LocalDateTime suspendedUntil) throws IOException {
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(ErrorCode.SUSPENDED_MEMBER.getStatus().value());
    response.setCharacterEncoding("UTF-8");

    SuspendedMemberResponse suspendedMemberResponse = SuspendedMemberResponse.builder()
        .errorCode(ErrorCode.SUSPENDED_MEMBER.name())
        .suspendReason(suspendReason)
        .suspendedUntil(suspendedUntil)
        .build();

    ObjectMapper suspendedResponseMapper = new ObjectMapper();
    suspendedResponseMapper.registerModule(new JavaTimeModule());
    suspendedResponseMapper.writeValue(response.getWriter(), suspendedMemberResponse);
  }

  /**
   * 화이트리스트 경로 확인 (인증x)
   *
   * @param uri 요청된 URI
   * @return 화이트리스트 여부
   */
  private boolean isWhitelistedPath(String uri) {
    return SecurityUrls.AUTH_WHITELIST.stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, uri))
        || SecurityUrls.SECURED_API_URLS.stream()
        .anyMatch(pattern -> pathMatcher.match(pattern, uri));
  }

}
