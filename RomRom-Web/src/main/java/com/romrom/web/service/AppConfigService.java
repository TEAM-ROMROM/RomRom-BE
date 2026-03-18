package com.romrom.web.service;

import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.web.dto.SystemResponse;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppConfigService {

  private static final String KEY_MIN_VERSION = "app.min.version";
  private static final String KEY_LATEST_VERSION = "app.latest.version";
  private static final String KEY_STORE_ANDROID = "app.store.android";
  private static final String KEY_STORE_IOS = "app.store.ios";

  private static final Pattern SEMVER_FORMAT_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigCacheService cacheService;

  @Transactional(readOnly = true)
  public SystemResponse checkVersion() {
    return SystemResponse.builder()
        .minimumVersion(getConfigValue(KEY_MIN_VERSION))
        .latestVersion(getConfigValue(KEY_LATEST_VERSION))
        .androidStoreUrl(getConfigValue(KEY_STORE_ANDROID))
        .iosStoreUrl(getConfigValue(KEY_STORE_IOS))
        .build();
  }

  @Transactional
  public SystemResponse updateLatestVersion(String version) {
    if (version == null || version.isBlank()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    String trimmedVersion = version.trim();
    if (!SEMVER_FORMAT_PATTERN.matcher(trimmedVersion).matches()) {
      log.warn("앱 버전 형식 오류: {}", trimmedVersion);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    SystemConfig latestVersionConfig = systemConfigRepository.findByConfigKey(KEY_LATEST_VERSION)
        .orElseGet(() -> SystemConfig.builder()
            .configKey(KEY_LATEST_VERSION)
            .description("앱 최신 버전")
            .build());
    latestVersionConfig.setConfigValue(trimmedVersion);
    systemConfigRepository.save(latestVersionConfig);

    cacheService.put(KEY_LATEST_VERSION, trimmedVersion);
    log.info("앱 최신 버전 업데이트: {}", trimmedVersion);

    return checkVersion();
  }

  private String getConfigValue(String configKey) {
    String cachedValue = cacheService.get(configKey);
    if (cachedValue != null) {
      return cachedValue;
    }
    String dbValue = systemConfigRepository.findByConfigKey(configKey)
        .map(systemConfig -> systemConfig.getConfigValue() != null ? systemConfig.getConfigValue() : "")
        .orElse("");
    if (!dbValue.isEmpty()) {
      cacheService.put(configKey, dbValue);
    }
    return dbValue;
  }
}
