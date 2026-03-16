package com.romrom.web.service;

import com.romrom.common.repository.SystemConfigRepository;
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
   * SystemConfig에서 버전 설정값을 조회하여 반환
   * 버전 비교 및 플랫폼 분기는 클라이언트에서 처리
   */
  @Transactional(readOnly = true)
  public SystemResponse checkVersion() {
    return SystemResponse.builder()
        .minimumVersion(getConfigValue("app.min.version"))
        .latestVersion(getConfigValue("app.latest.version"))
        .androidStoreUrl(getConfigValue("app.store.android"))
        .iosStoreUrl(getConfigValue("app.store.ios"))
        .build();
  }

  private String getConfigValue(String key) {
    return systemConfigRepository.findByConfigKey(key)
        .map(config -> config.getConfigValue() != null ? config.getConfigValue() : "")
        .orElse("");
  }
}
