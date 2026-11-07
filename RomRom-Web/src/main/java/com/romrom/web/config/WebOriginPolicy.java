package com.romrom.web.config;

import com.romrom.common.constant.ServiceDomain;
import java.util.List;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 허용 웹 Origin 단일 원본: CORS(SecurityConfig)·관리자 로그 WebSocket(JwtLogHandshakeInterceptor) 공통
 * 운영 도메인 이전 시 이 파일만 수정
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebOriginPolicy {

  // 운영 베이스 도메인 (원본은 ServiceDomain)
  public static final String SERVICE_BASE_DOMAIN = ServiceDomain.SERVICE_BASE_DOMAIN;

  // 로컬 개발 host:port (API 서버, 웹 개발 서버)
  public static final List<String> LOCAL_DEVELOPMENT_HOSTS = List.of("localhost:8080", "localhost:3000");

  /**
   * CORS 허용 Origin 패턴 4개: https 베이스 도메인, https 서브도메인, 로컬 2개(http)
   */
  public static List<String> toCorsAllowedOriginPatterns() {
    Stream<String> serviceOriginPatterns = Stream.of(
        "https://" + SERVICE_BASE_DOMAIN,
        "https://*." + SERVICE_BASE_DOMAIN);
    Stream<String> localOriginPatterns = LOCAL_DEVELOPMENT_HOSTS.stream().map(localHost -> "http://" + localHost);
    return Stream.concat(serviceOriginPatterns, localOriginPatterns).toList();
  }
}
