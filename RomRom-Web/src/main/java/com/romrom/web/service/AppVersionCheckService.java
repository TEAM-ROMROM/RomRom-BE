package com.romrom.web.service;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.common.util.VersionUtil;
import com.romrom.web.dto.SystemRequest;
import com.romrom.web.dto.SystemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppVersionCheckService {

  private final SystemConfigRepository systemConfigRepository;

  /**
   * 앱 버전 체크
   * 요청된 앱 버전과 SystemConfig의 최소/최신 버전을 비교하여 업데이트 필요 여부 반환
   */
  @Transactional(readOnly = true)
  public SystemResponse checkVersion(SystemRequest request) {
    String currentVersion = request.getAppVersion();
    String minVersion = getConfigValue("app.min.version");
    String latestVersion = getConfigValue("app.latest.version");

    boolean forceUpdate = VersionUtil.isUpdateRequired(currentVersion, minVersion);
    boolean recommendUpdate = !forceUpdate && VersionUtil.isUpdateRequired(currentVersion, latestVersion);
    String storeUrl = resolveStoreUrl(request.getPlatform());

    log.info("앱 버전 체크: currentVersion={}, minVersion={}, latestVersion={}, forceUpdate={}, recommendUpdate={}",
        currentVersion, minVersion, latestVersion, forceUpdate, recommendUpdate);

    return SystemResponse.builder()
        .forceUpdate(forceUpdate)
        .recommendUpdate(recommendUpdate)
        .latestVersion(latestVersion)
        .storeUrl(storeUrl)
        .build();
  }

  private String resolveStoreUrl(DeviceType platform) {
    if (platform == null) {
      return "";
    }
    return switch (platform) {
      case IOS -> getConfigValue("app.store.ios");
      case ANDROID -> getConfigValue("app.store.android");
      default -> "";
    };
  }

  private String getConfigValue(String key) {
    return systemConfigRepository.findByConfigKey(key)
        .map(config -> config.getConfigValue() != null ? config.getConfigValue() : "")
        .orElse("");
  }
}
