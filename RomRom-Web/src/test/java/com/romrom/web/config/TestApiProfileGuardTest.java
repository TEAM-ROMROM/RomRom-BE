package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.romrom.application.service.TestService;
import com.romrom.web.controller.api.AuthController;
import com.romrom.web.controller.api.TestController;
import java.util.Arrays;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * 개발용 테스트 API 운영 차단·인증 API 로그 노출 회귀 검증 (#813)
 * 컨텍스트 없이 annotation만 확인 (DB·Redis 불필요)
 */
class TestApiProfileGuardTest {

  // prod 제외 프로필 표현식
  private static final String[] NON_PROD_PROFILE_EXPRESSION = {"!prod"};

  @Test
  @DisplayName("TestController·TestService는 prod 프로필에서 빈으로 등록되지 않는다")
  void 테스트_API는_prod_프로필에서_제외된다() {
    assertProfileExcludesProd(TestController.class);
    assertProfileExcludesProd(TestService.class);
  }

  @Test
  @DisplayName("토큰을 주고받는 인증 API에는 요청·응답 본문을 남기는 @LogMonitor가 없다")
  void 인증_API는_LogMonitor를_쓰지_않는다() {
    boolean hasLogMonitorMethod = Arrays.stream(AuthController.class.getDeclaredMethods())
        .anyMatch(authMethod -> authMethod.isAnnotationPresent(LogMonitor.class));
    assertFalse(hasLogMonitorMethod, "AuthController 메서드에 @LogMonitor가 붙어 있다");
  }

  private void assertProfileExcludesProd(Class<?> targetClass) {
    Profile profile = targetClass.getAnnotation(Profile.class);
    assertNotNull(profile, targetClass.getSimpleName() + "에 @Profile이 없다");
    assertArrayEquals(NON_PROD_PROFILE_EXPRESSION, profile.value());
  }
}
