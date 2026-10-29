package com.romrom.auth.dto;

import java.util.Arrays;
import java.util.List;

/**
 * Security 관련 URL 상수 관리
 */
public class SecurityUrls {

  /**
   * 인증을 생략할 URL 패턴 목록
   */
  public static final List<String> AUTH_WHITELIST = Arrays.asList(

    // health
    "/actuator/health",

    // auth
    "/api/auth/login",                    // Firebase 통합 로그인
    "/api/auth/reissue",                  // accessToken 재발급
    "/api/auth/kakao/firebase-token",     // 카카오 Firebase Custom Token 발급

    // public item - 카카오톡 공유 OG 태그 렌더링용 (Cloud Function 경유 호출)
    "/api/item/public/get",

    // test-api
    "/api/test/sign-up", // 테스트 회원가입
    "/api/test/send/notification/all", // 테스트 전체 알람 발송

    // chat
    // "/chat/**",       // WebSecurityCustomizer에서 처리 (Spring Security 필터 체인 완전 제외)

    // Favicon
    "/favicon.ico",

    // Static Resources for AdminLTE
    "/css/**",
    "/js/**",
    "/plugins/**",
    "/dist/**",
    "/assets/**",
    "/UI/**",
    "/forms/**",
    "/layout/**",
    "/tables/**",
    "/widgets/**",
    "/generate/**",

    // Admin Login Page
    "/admin/login",
    "/admin/logout",
    "/api/admin/login",
    "/api/admin/logout"

  );

  /**
   * HMAC + Timestamp 서명 검증이 필요한 오픈 API URL 패턴 목록
   * JWT 인증은 스킵하고, @SecuredApi AOP에서 HMAC 서명 검증을 수행
   */
  public static final List<String> SECURED_API_URLS = Arrays.asList(
    "/api/app/version/check",   // 앱 버전 체크
    "/api/app/version/update"   // 앱 최신 버전 업데이트 (CI/CD)
    // 디버그 로그 스트리밍은 WebSocket(/ws/debug-logs)으로 전환됨 — handshake에서 HMAC 검증 (#788)
  );

  /**
   * 관리자 권한(ROLE_ADMIN) 필요 경로: 관리자 페이지·API 전체 + 메트릭
   * 로그인·로그아웃은 AUTH_WHITELIST가 먼저 매칭되어 허용 (개별 나열 시 신규 엔드포인트 누락 방지)
   */
  public static final List<String> ADMIN_PATHS = Arrays.asList(
    "/admin",
    "/admin/**",
    "/api/admin/**",

    // Actuator - 메트릭 엔드포인트 (일반 회원 접근 차단, 관리자 전용)
    "/actuator/prometheus",
    "/actuator/metrics"
  );

  /**
   * 관리자 인증 관련 엔드포인트 (필터에서 건너뛸 경로)
   */
  public static final List<String> ADMIN_AUTH_ENDPOINTS = Arrays.asList(
    "/api/admin/login",
    "/api/admin/logout",
    "/admin/login",
    "/admin/logout"
  );

}
