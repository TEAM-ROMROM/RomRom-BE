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

      // auth
      "/api/auth/sign-in", // OAuth 로그인
      "/api/auth/reissue", // accessToken 재발급

      // test-api
      "/api/test/sign-up", // 테스트 회원가입

      // Swagger
      "/docs/**",         // Swagger UI
      "/v3/api-docs/**",  // Swagger API 문서
      "/api/test/**"   // FIXME: TEST API : 개발중 임시 허용

  );

  /**
   * 관리자 권한이 필요한 URL 패턴 목록
   */
  public static final List<String> ADMIN_PATHS = Arrays.asList(
      // API

  );

}
