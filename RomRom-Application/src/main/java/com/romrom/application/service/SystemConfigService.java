package com.romrom.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.properties.SuhAiderProperties;

import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.ai.service.CategoryMatchingService;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.SystemConfigRepository;
import com.romrom.common.service.SystemConfigCacheService;
import com.romrom.common.service.UgcFilterService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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

  // 서버 점검 모드 관련 SystemConfig 키 상수
  private static final String KEY_MAINTENANCE_ENABLED = "server.maintenance.enabled";
  private static final String KEY_MAINTENANCE_MESSAGE = "server.maintenance.message";
  private static final String KEY_MAINTENANCE_END_TIME = "server.maintenance.end-time";

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigCacheService systemConfigCacheService;
  private final SuhAiderProperties suhAiderProperties;
  private final VertexAiProperties vertexAiProperties;
  private final AdminAlertConfigService adminAlertConfigService;
  private final UgcFilterService ugcFilterService;
  private final ObjectMapper objectMapper;
  private final CategoryMatchingService categoryMatchingService;

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    loadAllToRedis();
    adminAlertConfigService.initializeAlertConfig();
    categoryMatchingService.initializeCategoryEmbeddings();
  }

  @Transactional(readOnly = true)
  public void loadAllToRedis() {
    List<SystemConfig> allSystemConfigs = systemConfigRepository.findAll();
    Map<String, String> allSystemConfigMap = new HashMap<>();
    for (SystemConfig systemConfig : allSystemConfigs) {
      if (systemConfig.getConfigValue() != null) {
        allSystemConfigMap.put(systemConfig.getConfigKey(), systemConfig.getConfigValue());
      }
    }
    systemConfigCacheService.putAll(allSystemConfigMap);
    applyToProperties(allSystemConfigMap);
    log.info("시스템 설정 DB → Redis 로딩 완료: {} 건", allSystemConfigMap.size());
  }

  public AdminResponse getAiConfig() {
    Map<String, String> aiConfigMap = systemConfigCacheService.getByPrefix("ai.");
    return AdminResponse.builder()
        .aiPrimaryProvider(aiConfigMap.get("ai.primary.provider"))
        .aiFallbackProvider(aiConfigMap.get("ai.fallback.provider"))
        .aiOllamaEnabled(aiConfigMap.get("ai.ollama.enabled"))
        .aiOllamaBaseUrl(aiConfigMap.get("ai.ollama.base-url"))
        .aiOllamaChatModel(aiConfigMap.get("ai.ollama.chat-model"))
        .aiOllamaEmbeddingModel(aiConfigMap.get("ai.ollama.embedding-model"))
        .aiVertexEnabled(aiConfigMap.get("ai.vertex.enabled"))
        .aiVertexGenerationModel(aiConfigMap.get("ai.vertex.generation-model"))
        .aiVertexEmbeddingModel(aiConfigMap.get("ai.vertex.embedding-model"))
        .aiVertexGenerationLocation(aiConfigMap.get("ai.vertex.generation-location"))
        .aiVertexEmbeddingLocation(aiConfigMap.get("ai.vertex.embedding-location"))
        .aiPromptPricePredictionInstruction(aiConfigMap.get("ai.prompt.price-prediction.instruction"))
        .aiPromptChatRecommendationInstruction(aiConfigMap.get("ai.chat.recommendation.prompt.instruction"))
        .aiPromptChatRecommendationEnabled(aiConfigMap.get("ai.chat.recommendation.prompt.enabled"))
        .build();
  }

  @Transactional
  public AdminResponse updateAiConfig(AdminRequest adminRequest) {
    Map<String, String> aiConfigMap = buildAiConfigMap(adminRequest);
    Map<String, String> savedAiConfigMap = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : aiConfigMap.entrySet()) {
      String configKey = entry.getKey();
      String configValue = entry.getValue();

      String trimmedConfigValue = configValue != null ? configValue.trim() : "";

      if (trimmedConfigValue.isEmpty()) {
        log.debug("빈 값 무시 - 기존 설정 유지: {}", configKey);
        continue;
      }

      String aiConfigDescription = switch (configKey) {
        case "ai.primary.provider" -> "AI 기본 프로바이더";
        case "ai.fallback.provider" -> "AI 폴백 프로바이더";
        case "ai.ollama.enabled" -> "Ollama 활성화 여부";
        case "ai.ollama.base-url" -> "Ollama Base URL";
        case "ai.ollama.chat-model" -> "Ollama Chat 모델";
        case "ai.ollama.embedding-model" -> "Ollama Embedding 모델";
        case "ai.vertex.enabled" -> "Vertex AI 활성화 여부";
        case "ai.vertex.generation-model" -> "Vertex AI Generation 모델";
        case "ai.vertex.embedding-model" -> "Vertex AI Embedding 모델";
        case "ai.vertex.generation-location" -> "Vertex AI Generation 위치";
        case "ai.vertex.embedding-location" -> "Vertex AI Embedding 위치";
        case "ai.prompt.price-prediction.instruction" -> "가격 예측 AI System Prompt 본문 ({{INPUT_TEXT}} 치환 템플릿)";
        case "ai.chat.recommendation.prompt.instruction" -> "채팅 추천 AI System Prompt 본문";
        case "ai.chat.recommendation.prompt.enabled" -> "채팅 추천 AI 활성화 여부";
        default -> null;
      };

      SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
          .orElseGet(() -> SystemConfig.builder().configKey(configKey).description(aiConfigDescription).build());
      config.setConfigValue(trimmedConfigValue);
      systemConfigRepository.save(config);

      systemConfigCacheService.put(configKey, trimmedConfigValue);
      savedAiConfigMap.put(configKey, trimmedConfigValue);
    }
    applyToProperties(savedAiConfigMap);
    log.info("AI 설정 업데이트 완료: {} 건", savedAiConfigMap.size());
    return getAiConfig();
  }

  private Map<String, String> buildAiConfigMap(AdminRequest adminRequest) {
    Map<String, String> aiConfigMap = new LinkedHashMap<>();
    if (adminRequest.getAiPrimaryProvider() != null) aiConfigMap.put("ai.primary.provider", adminRequest.getAiPrimaryProvider());
    if (adminRequest.getAiFallbackProvider() != null) aiConfigMap.put("ai.fallback.provider", adminRequest.getAiFallbackProvider());
    if (adminRequest.getAiOllamaEnabled() != null) aiConfigMap.put("ai.ollama.enabled", adminRequest.getAiOllamaEnabled());
    if (adminRequest.getAiOllamaBaseUrl() != null) aiConfigMap.put("ai.ollama.base-url", adminRequest.getAiOllamaBaseUrl());
    if (adminRequest.getAiOllamaChatModel() != null) aiConfigMap.put("ai.ollama.chat-model", adminRequest.getAiOllamaChatModel());
    if (adminRequest.getAiOllamaEmbeddingModel() != null) aiConfigMap.put("ai.ollama.embedding-model", adminRequest.getAiOllamaEmbeddingModel());
    if (adminRequest.getAiVertexEnabled() != null) aiConfigMap.put("ai.vertex.enabled", adminRequest.getAiVertexEnabled());
    if (adminRequest.getAiVertexGenerationModel() != null) aiConfigMap.put("ai.vertex.generation-model", adminRequest.getAiVertexGenerationModel());
    if (adminRequest.getAiVertexEmbeddingModel() != null) aiConfigMap.put("ai.vertex.embedding-model", adminRequest.getAiVertexEmbeddingModel());
    if (adminRequest.getAiVertexGenerationLocation() != null) aiConfigMap.put("ai.vertex.generation-location", adminRequest.getAiVertexGenerationLocation());
    if (adminRequest.getAiVertexEmbeddingLocation() != null) aiConfigMap.put("ai.vertex.embedding-location", adminRequest.getAiVertexEmbeddingLocation());
    if (adminRequest.getAiPromptPricePredictionInstruction() != null) aiConfigMap.put("ai.prompt.price-prediction.instruction", adminRequest.getAiPromptPricePredictionInstruction());
    if (adminRequest.getAiPromptChatRecommendationInstruction() != null) aiConfigMap.put("ai.chat.recommendation.prompt.instruction", adminRequest.getAiPromptChatRecommendationInstruction());
    if (adminRequest.getAiPromptChatRecommendationEnabled() != null) aiConfigMap.put("ai.chat.recommendation.prompt.enabled", adminRequest.getAiPromptChatRecommendationEnabled());
    return aiConfigMap;
  }

  @Transactional(readOnly = true)
  public void reloadCache() {
    loadAllToRedis();
    log.info("시스템 설정 캐시 리로드 완료");
  }

  public AdminResponse getAppVersionConfig() {
    Map<String, String> appVersionConfigMap = systemConfigCacheService.getByPrefix("app.");
    return AdminResponse.builder()
        .appLatestVersion(appVersionConfigMap.get("app.latest.version"))
        .appMinVersion(appVersionConfigMap.get("app.min.version"))
        .appStoreAndroid(appVersionConfigMap.get("app.store.android"))
        .appStoreIos(appVersionConfigMap.get("app.store.ios"))
        .build();
  }

  @Transactional
  public AdminResponse updateAppVersionConfig(AdminRequest adminRequest) {
    Map<String, String> appVersionConfigMap = buildAppVersionConfigMap(adminRequest);
    for (Map.Entry<String, String> entry : appVersionConfigMap.entrySet()) {
      String configKey = entry.getKey();
      String configValue = entry.getValue();

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
    return getAppVersionConfig();
  }

  private Map<String, String> buildAppVersionConfigMap(AdminRequest adminRequest) {
    Map<String, String> appVersionConfigMap = new LinkedHashMap<>();
    if (adminRequest.getAppMinVersion() != null) appVersionConfigMap.put("app.min.version", adminRequest.getAppMinVersion());
    if (adminRequest.getAppStoreAndroid() != null) appVersionConfigMap.put("app.store.android", adminRequest.getAppStoreAndroid());
    if (adminRequest.getAppStoreIos() != null) appVersionConfigMap.put("app.store.ios", adminRequest.getAppStoreIos());
    return appVersionConfigMap;
  }

  /**
   * 서버 점검 모드 설정 조회
   * Redis 캐시에서 점검 관련 3개 키를 읽어 AdminResponse로 반환
   */
  public AdminResponse getMaintenanceConfig() {
    return AdminResponse.builder()
        .maintenanceEnabled(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_ENABLED, "false"))
        .maintenanceMessage(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_MESSAGE, ""))
        .maintenanceEndTime(systemConfigCacheService.getOrDefault(KEY_MAINTENANCE_END_TIME, ""))
        .build();
  }

  /**
   * 서버 점검 모드 설정 업데이트
   * null이 아닌 필드만 선택적으로 갱신 (PATCH 방식)
   */
  @Transactional
  public AdminResponse updateMaintenanceConfig(AdminRequest adminRequest) {
    if (adminRequest.getMaintenanceEnabled() != null) {
      String enabledValue = adminRequest.getMaintenanceEnabled().trim();
      // "true"/"false" 이외의 값은 허용하지 않음
      if (!enabledValue.equals("true") && !enabledValue.equals("false")) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
      upsertMaintenanceConfig(KEY_MAINTENANCE_ENABLED, enabledValue, "서버 점검 모드 활성화 여부");
    }

    if (adminRequest.getMaintenanceMessage() != null) {
      upsertMaintenanceConfig(KEY_MAINTENANCE_MESSAGE, adminRequest.getMaintenanceMessage().trim(), "서버 점검 안내 메시지");
    }

    if (adminRequest.getMaintenanceEndTime() != null) {
      upsertMaintenanceConfig(KEY_MAINTENANCE_END_TIME, adminRequest.getMaintenanceEndTime().trim(), "서버 점검 예상 종료 시간");
    }

    log.info("서버 점검 모드 설정 업데이트 완료: enabled={}", adminRequest.getMaintenanceEnabled());
    return getMaintenanceConfig();
  }

  /**
   * 점검 설정 단건 upsert: DB 저장 + Redis 캐시 동기화
   */
  private void upsertMaintenanceConfig(String configKey, String configValue, String description) {
    SystemConfig maintenanceConfig = systemConfigRepository.findByConfigKey(configKey)
        .orElseGet(() -> SystemConfig.builder().configKey(configKey).description(description).build());
    maintenanceConfig.setConfigValue(configValue);
    systemConfigRepository.save(maintenanceConfig);
    systemConfigCacheService.put(configKey, configValue);
  }

  public AdminResponse getUgcFilterConfig() {
    String ugcPatternJson = systemConfigCacheService.getOrDefault("ugc.filter.patterns", "[]");
    return AdminResponse.builder()
        .ugcFilterPatterns(ugcPatternJson)
        .build();
  }

  @Transactional
  public AdminResponse updateUgcFilterConfig(AdminRequest adminRequest) {
    String newUgcPatternsJson = adminRequest.getUgcFilterPatterns();

    if (newUgcPatternsJson == null || newUgcPatternsJson.isBlank()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // JSON 파싱 유효성 검사
    List<String> newUgcPatternStrings;
    try {
      newUgcPatternStrings = objectMapper.readValue(newUgcPatternsJson, new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("UGC 필터 패턴 JSON 파싱 실패: {}", e.getMessage());
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // null 또는 빈 문자열 요소 검증
    if (newUgcPatternStrings == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    newUgcPatternStrings.removeIf(ugcPattern -> ugcPattern == null || ugcPattern.isBlank());

    // 각 패턴 정규식 컴파일 유효성 검사
    for (String ugcPatternString : newUgcPatternStrings) {
      try {
        Pattern.compile(ugcPatternString);
      } catch (PatternSyntaxException e) {
        log.warn("유효하지 않은 정규식 패턴: {}", ugcPatternString);
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    }

    // DB upsert
    SystemConfig ugcFilterConfig = systemConfigRepository.findByConfigKey("ugc.filter.patterns")
        .orElseGet(() -> SystemConfig.builder()
            .configKey("ugc.filter.patterns")
            .description("UGC 텍스트 필터링 정규식 패턴 목록 (JSON 배열)")
            .build());
    ugcFilterConfig.setConfigValue(newUgcPatternsJson);
    systemConfigRepository.save(ugcFilterConfig);

    // Redis 캐시 업데이트
    systemConfigCacheService.put("ugc.filter.patterns", newUgcPatternsJson);

    // UgcFilterService 인메모리 패턴 캐시 무효화
    ugcFilterService.invalidateCompiledPatternCache();

    log.info("UGC 필터 패턴 업데이트 완료: {} 개 패턴", newUgcPatternStrings.size());
    return getUgcFilterConfig();
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
