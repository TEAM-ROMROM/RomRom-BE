package com.romrom.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 클라이언트 IP: 프록시가 넣는 X-Real-IP > 접속 주소
 * 위조 가능한 X-Forwarded-For 는 쓰지 않음 (요청 제한·관리자 작업 기록 공통)
 */
public final class ClientIpAddressResolver {

  private static final String REAL_CLIENT_IP_HEADER = "X-Real-IP";

  private ClientIpAddressResolver() {
  }

  public static String resolveClientIpAddress(HttpServletRequest httpServletRequest) {
    String realClientIpAddress = httpServletRequest.getHeader(REAL_CLIENT_IP_HEADER);
    return realClientIpAddress != null && !realClientIpAddress.isBlank()
        ? realClientIpAddress.trim()
        : httpServletRequest.getRemoteAddr();
  }
}
