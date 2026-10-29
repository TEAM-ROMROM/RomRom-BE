package com.romrom.member.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그인 이력 요청 정보 추출 검증 (#830): 요청 스레드에서 IP·UA·기기 추출 > 저장, 기기 판별 우선순위
 */
@ExtendWith(MockitoExtension.class)
class LoginHistoryRequestContextTest {

  @Mock
  private LoginHistoryRepository loginHistoryRepository;

  @InjectMocks
  private LoginHistoryService loginHistoryService;

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("요청 스레드의 IP(X-Forwarded-For 첫 값)·UA·플랫폼 헤더가 저장된다")
  void 요청_정보가_이력에_저장된다() {
    MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", "/api/auth/login");
    loginRequest.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
    loginRequest.addHeader("User-Agent", "Dart/3.5 (dart:io)");
    loginRequest.addHeader(LoginHistoryService.CLIENT_PLATFORM_HEADER, "IOS");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(loginRequest));
    // 단위 테스트에서는 프록시 대신 자기 자신 (비동기 없이 바로 저장)
    ReflectionTestUtils.setField(loginHistoryService, "self", loginHistoryService);

    loginHistoryService.recordFromCurrentRequest(UUID.randomUUID(), SocialPlatform.KAKAO, LoginResult.SUCCESS, null);

    ArgumentCaptor<LoginHistory> loginHistoryCaptor = ArgumentCaptor.forClass(LoginHistory.class);
    verify(loginHistoryRepository).save(loginHistoryCaptor.capture());
    LoginHistory savedLoginHistory = loginHistoryCaptor.getValue();
    assertEquals("203.0.113.7", savedLoginHistory.getIpAddress());
    assertEquals("Dart/3.5 (dart:io)", savedLoginHistory.getUserAgent());
    assertEquals(DeviceType.IOS, savedLoginHistory.getDeviceType());
  }

  @Test
  @DisplayName("기기: 플랫폼 헤더 > UA 키워드 > OTHER, 헤더 대소문자·공백 무시")
  void 기기_판별_우선순위() {
    assertEquals(DeviceType.ANDROID, LoginHistoryService.resolveDeviceType(" android ", "Mozilla/5.0 (iPhone)"));
    assertEquals(DeviceType.IOS, LoginHistoryService.resolveDeviceType(null, "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0)"));
    assertEquals(DeviceType.ANDROID, LoginHistoryService.resolveDeviceType("WINDOWS", "okhttp Android 14"));
    assertEquals(DeviceType.OTHER, LoginHistoryService.resolveDeviceType(null, "Dart/3.5 (dart:io)"));
    assertEquals(DeviceType.OTHER, LoginHistoryService.resolveDeviceType(null, null));
  }
}
