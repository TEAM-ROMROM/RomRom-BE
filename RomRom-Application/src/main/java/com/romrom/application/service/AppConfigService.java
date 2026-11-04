package com.romrom.application.service;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.dto.SystemResponse;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.common.service.SystemConfigCacheService;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppConfigService {

  private static final Pattern SEMVER_FORMAT_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigCacheService systemConfigCacheService;

  @Transactional(readOnly = true)
  public SystemResponse checkVersion() {
    String maintenanceEnabledValue = getConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_ENABLED);
    String legacyLatestVersion = getConfigValue(SystemConfigKeys.APP_LATEST_VERSION);
    return SystemResponse.builder()
        .minimumVersion(getConfigValue(SystemConfigKeys.APP_MIN_VERSION))
        .latestVersion(legacyLatestVersion)
        .latestVersionIos(getConfigValueOrDefault(SystemConfigKeys.APP_LATEST_VERSION_IOS, legacyLatestVersion))
        .latestVersionAndroid(getConfigValueOrDefault(SystemConfigKeys.APP_LATEST_VERSION_ANDROID, legacyLatestVersion))
        .androidStoreUrl(getConfigValue(SystemConfigKeys.APP_STORE_ANDROID))
        .iosStoreUrl(getConfigValue(SystemConfigKeys.APP_STORE_IOS))
        .maintenanceEnabled("true".equals(maintenanceEnabledValue))
        .maintenanceMessage(getConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_MESSAGE))
        .maintenanceEndTime(getConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_END_TIME))
        .build();
  }

  /**
   * 최신 버전 갱신: 형식 검증 > 플랫폼 없음 > 공통 키만 (구 워크플로우 호환)
   * 플랫폼 있음 > 플랫폼 키 > 공통 키 = 두 플랫폼 중 낮은 버전 (한쪽 미설정 > 기존 공통 값과 비교)
   */
  @Transactional
  public SystemResponse updateLatestVersion(String version, DeviceType platform) {
    String trimmedVersion = validateSemanticVersion(version);
    if (platform == null) {
      saveConfigValue(SystemConfigKeys.APP_LATEST_VERSION, trimmedVersion, "앱 최신 버전");
      log.info("[app version] latest updated : {}", trimmedVersion);
      return checkVersion();
    }

    String platformConfigKey = toPlatformLatestVersionKey(platform);
    String otherPlatformConfigKey = platform == DeviceType.IOS
        ? SystemConfigKeys.APP_LATEST_VERSION_ANDROID
        : SystemConfigKeys.APP_LATEST_VERSION_IOS;
    saveConfigValue(platformConfigKey, trimmedVersion, platform.name() + " 앱 최신 버전 (스토어 출시 기준)");

    // 구버전 앱용 공통 최신 버전: 두 스토어 모두 출시된 버전까지만
    String legacyLatestVersion = getConfigValue(SystemConfigKeys.APP_LATEST_VERSION);
    String otherPlatformVersion = getConfigValueOrDefault(otherPlatformConfigKey, legacyLatestVersion);
    String commonLatestVersion = otherPlatformVersion.isEmpty()
        ? trimmedVersion
        : lowerSemanticVersion(trimmedVersion, otherPlatformVersion);
    saveConfigValue(SystemConfigKeys.APP_LATEST_VERSION, commonLatestVersion, "앱 최신 버전");
    log.info("[app version] latest updated : platform={}, version={}, common={}",
        platform, trimmedVersion, commonLatestVersion);
    return checkVersion();
  }

  /**
   * semver(x.y.z) 형식 검증 후 앞뒤 공백 제거 값 (형식 오류 > 400)
   */
  private String validateSemanticVersion(String version) {
    if (version == null || version.isBlank()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    String trimmedVersion = version.trim();
    if (!SEMVER_FORMAT_PATTERN.matcher(trimmedVersion).matches()) {
      log.warn("앱 버전 형식 오류: {}", trimmedVersion);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return trimmedVersion;
  }

  /**
   * 플랫폼 > 최신 버전 설정 키 (ANDROID·IOS 외 > 400)
   */
  private String toPlatformLatestVersionKey(DeviceType platform) {
    return switch (platform) {
      case IOS -> SystemConfigKeys.APP_LATEST_VERSION_IOS;
      case ANDROID -> SystemConfigKeys.APP_LATEST_VERSION_ANDROID;
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    };
  }

  /**
   * 두 semver 중 낮은 버전 (형식 오류 값 > 다른 값)
   */
  static String lowerSemanticVersion(String firstVersion, String secondVersion) {
    if (!SEMVER_FORMAT_PATTERN.matcher(secondVersion).matches()) {
      return firstVersion;
    }
    if (!SEMVER_FORMAT_PATTERN.matcher(firstVersion).matches()) {
      return secondVersion;
    }
    String[] firstVersionParts = firstVersion.split("\\.");
    String[] secondVersionParts = secondVersion.split("\\.");
    for (int partIndex = 0; partIndex < firstVersionParts.length; partIndex++) {
      int versionPartComparison = Integer.compare(
          Integer.parseInt(firstVersionParts[partIndex]), Integer.parseInt(secondVersionParts[partIndex]));
      if (versionPartComparison != 0) {
        return versionPartComparison < 0 ? firstVersion : secondVersion;
      }
    }
    return firstVersion;
  }

  /**
   * 설정 저장 (없으면 생성) > Redis 캐시 동기화
   */
  private void saveConfigValue(String configKey, String configValue, String description) {
    SystemConfig systemConfig = systemConfigRepository.findByConfigKey(configKey)
        .orElseGet(() -> SystemConfig.builder()
            .configKey(configKey)
            .description(description)
            .build());
    systemConfig.setConfigValue(configValue);
    systemConfigRepository.save(systemConfig);
    systemConfigCacheService.put(configKey, configValue);
  }

  /**
   * 설정 값 (없음·빈 값 > 기본값)
   */
  private String getConfigValueOrDefault(String configKey, String defaultValue) {
    String configValue = getConfigValue(configKey);
    return configValue.isEmpty() ? defaultValue : configValue;
  }

  private String getConfigValue(String configKey) {
    String cachedValue = systemConfigCacheService.get(configKey);
    if (cachedValue != null) {
      return cachedValue;
    }
    String dbValue = systemConfigRepository.findByConfigKey(configKey)
        .map(systemConfig -> systemConfig.getConfigValue() != null ? systemConfig.getConfigValue() : "")
        .orElse("");
    if (!dbValue.isEmpty()) {
      systemConfigCacheService.put(configKey, dbValue);
    }
    return dbValue;
  }
}
