package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 허용 웹 Origin 검증 (#815): 운영 도메인 + 로컬, 소멸 도메인(romrom.xyz) 미포함
 */
class WebOriginPolicyTest {

  @Test
  @DisplayName("CORS 패턴은 운영 베이스 도메인·서브도메인(https)과 로컬 2개(http)")
  void CORS_허용_패턴은_운영과_로컬이다() {
    assertEquals(List.of(
        "https://romrom.suhsaechan.kr",
        "https://*.romrom.suhsaechan.kr",
        "http://localhost:8080",
        "http://localhost:3000"
    ), WebOriginPolicy.toCorsAllowedOriginPatterns());
  }

  @Test
  @DisplayName("소멸 도메인 romrom.xyz는 허용하지 않는다")
  void 소멸_도메인은_허용하지_않는다() {
    assertTrue(WebOriginPolicy.toCorsAllowedOriginPatterns().stream()
        .noneMatch(originPattern -> originPattern.contains("romrom.xyz")));
  }
}
