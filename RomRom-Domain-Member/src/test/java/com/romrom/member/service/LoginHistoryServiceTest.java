package com.romrom.member.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.LoginResult;
import com.romrom.common.constant.SocialPlatform;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.repository.mongo.LoginHistoryRepository;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * LoginHistoryService 단위 테스트 (Issue #708)
 *
 * 검증 대상:
 *  - SUCCESS / FAIL 저장 시 필드 매핑
 *  - memberId=null 일 때 save 호출 skip
 *  - repository.save 실패 시 예외 전파 없이 정상 종료
 */
@ExtendWith(MockitoExtension.class)
class LoginHistoryServiceTest {

  @Mock
  private LoginHistoryRepository loginHistoryRepository;

  @InjectMocks
  private LoginHistoryService loginHistoryService;

  @Test
  @DisplayName("record_성공_SUCCESS_저장된다")
  void record_성공_SUCCESS_저장된다() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    String clientIpAddress = "127.0.0.1";
    String clientUserAgent = "Mozilla/5.0 (iPhone)";

    // when
    loginHistoryService.record(
        targetMemberId,
        clientIpAddress,
        clientUserAgent,
        DeviceType.IOS,
        SocialPlatform.KAKAO,
        LoginResult.SUCCESS,
        null
    );

    // then
    ArgumentCaptor<LoginHistory> loginHistoryCaptor = ArgumentCaptor.forClass(LoginHistory.class);
    verify(loginHistoryRepository, times(1)).save(loginHistoryCaptor.capture());

    LoginHistory savedLoginHistory = loginHistoryCaptor.getValue();
    Assertions.assertEquals(targetMemberId, savedLoginHistory.getMemberId());
    Assertions.assertEquals(LoginResult.SUCCESS, savedLoginHistory.getLoginResult());
    Assertions.assertEquals(clientIpAddress, savedLoginHistory.getIpAddress());
    Assertions.assertEquals(clientUserAgent, savedLoginHistory.getUserAgent());
    Assertions.assertEquals(DeviceType.IOS, savedLoginHistory.getDeviceType());
    Assertions.assertEquals(SocialPlatform.KAKAO, savedLoginHistory.getSocialPlatform());
    Assertions.assertNull(savedLoginHistory.getFailReason());
    Assertions.assertNotNull(savedLoginHistory.getLoginAt());
  }

  @Test
  @DisplayName("record_실패_FAIL_저장된다")
  void record_실패_FAIL_저장된다() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    String loginFailReason = "INVALID_TOKEN";

    // when
    loginHistoryService.record(
        targetMemberId,
        null,
        null,
        DeviceType.OTHER,
        SocialPlatform.GOOGLE,
        LoginResult.FAIL,
        loginFailReason
    );

    // then
    ArgumentCaptor<LoginHistory> loginHistoryCaptor = ArgumentCaptor.forClass(LoginHistory.class);
    verify(loginHistoryRepository, times(1)).save(loginHistoryCaptor.capture());

    LoginHistory savedLoginHistory = loginHistoryCaptor.getValue();
    Assertions.assertEquals(targetMemberId, savedLoginHistory.getMemberId());
    Assertions.assertEquals(LoginResult.FAIL, savedLoginHistory.getLoginResult());
    Assertions.assertEquals(loginFailReason, savedLoginHistory.getFailReason());
  }

  @Test
  @DisplayName("record_memberId_null_skip")
  void record_memberId_null_skip() {
    // when
    loginHistoryService.record(
        null,
        "127.0.0.1",
        "ua",
        DeviceType.OTHER,
        SocialPlatform.KAKAO,
        LoginResult.SUCCESS,
        null
    );

    // then
    verify(loginHistoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("record_저장_실패시_예외_throw하지않음")
  void record_저장_실패시_예외_throw하지않음() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    when(loginHistoryRepository.save(any(LoginHistory.class)))
        .thenThrow(new RuntimeException("Mongo down"));

    // when & then - 예외가 전파되지 않아야 한다
    Assertions.assertDoesNotThrow(() -> loginHistoryService.record(
        targetMemberId,
        "127.0.0.1",
        "ua",
        DeviceType.ANDROID,
        SocialPlatform.KAKAO,
        LoginResult.SUCCESS,
        null
    ));

    verify(loginHistoryRepository, times(1)).save(any(LoginHistory.class));
  }
}
