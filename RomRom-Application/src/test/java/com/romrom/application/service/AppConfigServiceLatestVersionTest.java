package com.romrom.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.common.service.SystemConfigCacheService;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 플랫폼별 최신 버전 단위 테스트 (#858): 공통 최신 버전 = 두 플랫폼 중 낮은 버전
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppConfigServiceLatestVersionTest {

  @Mock
  private SystemConfigRepository systemConfigRepository;

  @Mock
  private SystemConfigCacheService systemConfigCacheService;

  @InjectMocks
  private AppConfigService appConfigService;

  @Test
  @DisplayName("semver 비교: 자리별 숫자 비교 (1.10.2 > 1.9.9)")
  void lowerSemanticVersion_숫자비교() {
    Assertions.assertEquals("1.9.9", AppConfigService.lowerSemanticVersion("1.10.2", "1.9.9"));
    Assertions.assertEquals("1.10.142", AppConfigService.lowerSemanticVersion("1.10.142", "1.10.143"));
    Assertions.assertEquals("1.10.142", AppConfigService.lowerSemanticVersion("1.10.142", "잘못된값"));
  }

  @Test
  @DisplayName("Android 출시 1.10.143, iOS 1.10.142 > 공통 최신 버전 1.10.142 유지")
  void updateLatestVersion_안드로이드먼저_공통은낮은버전() {
    when(systemConfigRepository.findByConfigKey(any())).thenReturn(Optional.empty());
    when(systemConfigCacheService.get(SystemConfigKeys.APP_LATEST_VERSION)).thenReturn("1.10.142");
    when(systemConfigCacheService.get(SystemConfigKeys.APP_LATEST_VERSION_IOS)).thenReturn("1.10.142");

    appConfigService.updateLatestVersion("1.10.143", DeviceType.ANDROID);

    verify(systemConfigCacheService).put(SystemConfigKeys.APP_LATEST_VERSION_ANDROID, "1.10.143");
    verify(systemConfigCacheService).put(SystemConfigKeys.APP_LATEST_VERSION, "1.10.142");
  }

  @Test
  @DisplayName("플랫폼 없음(구 워크플로우) > 공통 최신 버전만 갱신")
  void updateLatestVersion_플랫폼없음_공통만() {
    when(systemConfigRepository.findByConfigKey(any())).thenReturn(Optional.empty());

    appConfigService.updateLatestVersion("1.10.143", null);

    verify(systemConfigCacheService).put(SystemConfigKeys.APP_LATEST_VERSION, "1.10.143");
  }
}
