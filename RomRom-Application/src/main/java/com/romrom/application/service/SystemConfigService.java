package com.romrom.application.service;

import com.romrom.ai.properties.SuhAiderProperties;
import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.common.service.SystemConfigCacheService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigCacheService systemConfigCacheService;
  private final SuhAiderProperties suhAiderProperties;
  private final VertexAiProperties vertexAiProperties;
  private final AdminAlertConfigService adminAlertConfigService;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    loadAllToRedis();
    adminAlertConfigService.initializeAlertConfig();
  }

  @Transactional(readOnly = true)
  public void loadAllToRedis() {
    List<SystemConfig> configs = systemConfigRepository.findAll();
    Map<String, String> configMap = new HashMap<>();
    for (SystemConfig config : configs) {
      if (config.getConfigValue() != null) {
        configMap.put(config.getConfigKey(), config.getConfigValue());
      }
    }
    systemConfigCacheService.putAll(configMap);
    applyToProperties(configMap);
    log.info("시스템 설정 DB → Redis 로딩 완료: {} 건", configMap.size());
  }

  public Map<String, String> getAiConfig() {
    return systemConfigCacheService.getByPrefix("ai.");
  }

  @Transactional
  public void updateAiConfig(Map<String, String> aiConfigMap) {
    for (Map.Entry<String, String> entry : aiConfigMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      if (!key.startsWith("ai.")) {
        continue;
      }

      SystemConfig config = systemConfigRepository.findByConfigKey(key)
          .orElseGet(() -> SystemConfig.builder().configKey(key).build());
      config.setConfigValue(value);
      systemConfigRepository.save(config);

      systemConfigCacheService.put(key, value);
    }
    applyToProperties(aiConfigMap);
    log.info("AI 설정 업데이트 완료: {} 건", aiConfigMap.size());
  }

  @Transactional(readOnly = true)
  public void reloadCache() {
    loadAllToRedis();
    log.info("시스템 설정 캐시 리로드 완료");
  }

  public Map<String, String> getAppVersionConfig() {
    return systemConfigCacheService.getByPrefix("app.");
  }

  @Transactional
  public void updateAppVersionConfig(Map<String, String> appVersionConfigMap) {
    for (Map.Entry<String, String> entry : appVersionConfigMap.entrySet()) {
      String configKey = entry.getKey();
      String configValue = entry.getValue();

      if ("app.latest.version".equals(configKey)) {
        log.warn("허용되지 않은 앱 버전 설정 키 무시: {}", configKey);
        continue;
      }

      if (!configKey.startsWith("app.")) {
        log.warn("허용되지 않은 앱 버전 설정 키 무시: {}", configKey);
        continue;
      }

      String trimmedConfigValue = configValue != null ? configValue.trim() : "";

      if (trimmedConfigValue.isEmpty()) {
        log.debug("빈 값 무시 - 기존 설정 유지: {}", configKey);
        continue;
      }

      if ("app.min.version".equals(configKey)) {
        if (!trimmedConfigValue.matches("^\\d+\\.\\d+\\.\\d+$")) {
          log.warn("앱 버전 형식 오류: {}", trimmedConfigValue);
          throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
      }

      String configDescription = switch (configKey) {
        case "app.min.version" -> "앱 최소 필수 버전";
        case "app.store.android" -> "Android Google Play URL";
        case "app.store.ios" -> "iOS App Store URL";
        default -> null;
      };

      SystemConfig appVersionConfig = systemConfigRepository.findByConfigKey(configKey)
          .orElseGet(() -> SystemConfig.builder().configKey(configKey).description(configDescription).build());
      appVersionConfig.setConfigValue(trimmedConfigValue);
      systemConfigRepository.save(appVersionConfig);

      systemConfigCacheService.put(configKey, trimmedConfigValue);
    }
    log.info("앱 버전 설정 업데이트 완료");
  }

  private void applyToProperties(Map<String, String> configMap) {
    if (configMap.containsKey("ai.ollama.base-url")) {
      suhAiderProperties.setBaseUrl(configMap.get("ai.ollama.base-url"));
    }
    if (configMap.containsKey("ai.ollama.embedding-model")) {
      if (suhAiderProperties.getEmbedding() != null) {
        suhAiderProperties.getEmbedding().setDefaultModel(configMap.get("ai.ollama.embedding-model"));
      }
    }
    if (configMap.containsKey("ai.vertex.generation-model")) {
      vertexAiProperties.setGenerationModel(configMap.get("ai.vertex.generation-model"));
    }
    if (configMap.containsKey("ai.vertex.embedding-model")) {
      vertexAiProperties.setEmbeddingModel(configMap.get("ai.vertex.embedding-model"));
    }
    if (configMap.containsKey("ai.vertex.embedding-location")) {
      vertexAiProperties.setEmbeddingLocation(configMap.get("ai.vertex.embedding-location"));
    }
    if (configMap.containsKey("ai.vertex.generation-location")) {
      vertexAiProperties.setGenerationLocation(configMap.get("ai.vertex.generation-location"));
    }
  }
}
