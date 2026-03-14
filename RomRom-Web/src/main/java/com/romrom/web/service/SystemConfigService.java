package com.romrom.web.service;

import com.romrom.ai.properties.SuhAiderProperties;
import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.web.dto.SystemRequest;
import com.romrom.web.dto.SystemResponse;
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
  private final SystemConfigCacheService cacheService;
  private final SuhAiderProperties suhAiderProperties;
  private final VertexAiProperties vertexAiProperties;

  /**
   * 서버 기동 완료 후 DB → Redis 캐시 로딩
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    loadAllToRedis();
  }

  /**
   * DB에서 전체 설정을 Redis로 로딩 + Properties 빈 갱신
   */
  @Transactional(readOnly = true)
  public void loadAllToRedis() {
    List<SystemConfig> configs = systemConfigRepository.findAll();
    Map<String, String> configMap = new HashMap<>();
    for (SystemConfig config : configs) {
      if (config.getConfigValue() != null) {
        configMap.put(config.getConfigKey(), config.getConfigValue());
      }
    }
    cacheService.putAll(configMap);
    applyToProperties(configMap);
    log.info("시스템 설정 DB → Redis 로딩 완료: {} 건", configMap.size());
  }

  /**
   * AI 관련 설정 전체 조회 (캐시에서)
   */
  public Map<String, String> getAiConfig() {
    return cacheService.getByPrefix("ai.");
  }

  /**
   * AI 설정 일괄 업데이트 (DB + 캐시 + Properties)
   */
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

      cacheService.put(key, value);
    }
    applyToProperties(aiConfigMap);
    log.info("AI 설정 업데이트 완료: {} 건", aiConfigMap.size());
  }

  /**
   * 전체 캐시 리로드 (DB → Redis)
   */
  @Transactional(readOnly = true)
  public void reloadCache() {
    loadAllToRedis();
    log.info("시스템 설정 캐시 리로드 완료");
  }

  /**
   * 앱 버전 설정 전체 조회 (DB에서)
   */
  @Transactional(readOnly = true)
  public SystemResponse getVersionConfig() {
    return SystemResponse.builder()
        .minVersion(findConfigValue("app.min.version"))
        .latestVersion(findConfigValue("app.latest.version"))
        .storeIos(findConfigValue("app.store.ios"))
        .storeAndroid(findConfigValue("app.store.android"))
        .build();
  }

  /**
   * 앱 버전 설정 업데이트 (DB)
   */
  @Transactional
  public SystemResponse updateVersionConfig(SystemRequest request) {
    upsertConfig("app.min.version", request.getMinVersion());
    upsertConfig("app.latest.version", request.getLatestVersion());
    upsertConfig("app.store.ios", request.getStoreIos());
    upsertConfig("app.store.android", request.getStoreAndroid());
    log.info("앱 버전 설정 업데이트 완료");
    return getVersionConfig();
  }

  private void upsertConfig(String key, String value) {
    if (value == null) {
      return;
    }
    SystemConfig config = systemConfigRepository.findByConfigKey(key)
        .orElseGet(() -> SystemConfig.builder().configKey(key).build());
    config.setConfigValue(value);
    systemConfigRepository.save(config);
  }

  private String findConfigValue(String key) {
    return systemConfigRepository.findByConfigKey(key)
        .map(config -> config.getConfigValue() != null ? config.getConfigValue() : "")
        .orElse("");
  }

  /**
   * 설정 값을 Properties 빈에 반영 (런타임 갱신)
   */
  private void applyToProperties(Map<String, String> configMap) {
    // SuhAider (Ollama) Properties
    if (configMap.containsKey("ai.ollama.base-url")) {
      suhAiderProperties.setBaseUrl(configMap.get("ai.ollama.base-url"));
    }
    if (configMap.containsKey("ai.ollama.embedding-model")) {
      if (suhAiderProperties.getEmbedding() != null) {
        suhAiderProperties.getEmbedding().setDefaultModel(configMap.get("ai.ollama.embedding-model"));
      }
    }

    // Vertex AI Properties
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
