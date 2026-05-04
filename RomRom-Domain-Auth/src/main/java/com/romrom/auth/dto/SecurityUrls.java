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
    "/api/auth/login",   // Firebase 통합 로그인
    "/api/auth/reissue", // accessToken 재발급

    // public item - 카카오톡 공유 OG 태그 렌더링용 (Cloud Function 경유 호출)
    "/api/item/public/get",

    // test-api
    "/api/test/sign-up", // 테스트 회원가입
    "/api/test/send/notification/all", // 테스트 전체 알람 발송

    // chat
    // "/chat/**",       // WebSecurityCustomizer에서 처리 (Spring Security 필터 체인 완전 제외)

    // Swagger
    "/docs/**",         // Swagger UI
    "/v3/api-docs/**",  // Swagger API 문서
    "/api/test/**",   // FIXME: TEST API : 개발중 임시 허용

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
    "/api/app/version/update",  // 앱 최신 버전 업데이트 (CI/CD)
    "/api/app/debug/log-stream" // SSE 로그 스트리밍 (테스트 빌드 디버그)
  );

  /**
   * 관리자 권한이 필요한 URL 패턴 목록
   */
  public static final List<String> ADMIN_PATHS = Arrays.asList(
    // Admin Pages
    "/admin",
    "/admin/",
    "/admin/members",
    "/admin/members/{memberId}",
    "/admin/items",
    "/admin/reports",
    "/admin/announcements",
    "/admin/settings",

    // Admin APIs - Dashboard
    "/api/admin/dashboard/stats",
    "/api/admin/dashboard/recent-members",
    "/api/admin/dashboard/recent-items",

    // Admin APIs - Items
    "/api/admin/items/list",
    "/api/admin/items/delete",

    // Admin APIs - Members
    "/api/admin/members/list",
    "/api/admin/members/detail",
    "/api/admin/members/status",

    // Admin APIs - Reports
    "/api/admin/reports/item-list",
    "/api/admin/reports/member-list",
    "/api/admin/reports/item-detail",
    "/api/admin/reports/member-detail",
    "/api/admin/reports/update-status",
    "/api/admin/reports/stats",

    // Admin APIs - Announcements
    "/api/admin/announcements/list",
    "/api/admin/announcements/create",
    "/api/admin/announcements/delete",

    // Admin APIs - Config
    "/api/admin/config/ai/get",
    "/api/admin/config/ai/update",
    "/api/admin/config/cache/reload",
    "/api/admin/config/app-version/get",
    "/api/admin/config/app-version/update",

    // Admin APIs - UGC Filter Config
    "/api/admin/config/ugc-filter/get",
    "/api/admin/config/ugc-filter/update",

    // Admin APIs - Alert Config
    "/api/admin/alert-config/get",
    "/api/admin/alert-config/update",

    // Admin APIs - Maintenance Config
    "/api/admin/config/maintenance/get",
    "/api/admin/config/maintenance/update"
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
