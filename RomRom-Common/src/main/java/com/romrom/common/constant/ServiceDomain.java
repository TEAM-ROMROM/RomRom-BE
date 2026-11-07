package com.romrom.common.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 운영 도메인 단일 원본: CORS·WebSocket Origin(WebOriginPolicy), 메일 속 관리자 링크
 * 도메인 이전 시 이 파일만 수정
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceDomain {

  // 운영 베이스 도메인 (api.·admin. 등 서브도메인 허용)
  public static final String SERVICE_BASE_DOMAIN = "romrom.suhsaechan.kr";
  // API 서버 주소 (예: https://api.romrom.suhsaechan.kr)
  public static final String API_BASE_URL = "https://api." + SERVICE_BASE_DOMAIN;
  // 관리자 웹 주소 (API 서버가 /admin 제공)
  public static final String ADMIN_PAGE_BASE_URL = API_BASE_URL + "/admin";
}
