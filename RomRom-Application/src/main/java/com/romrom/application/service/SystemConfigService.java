package com.romrom.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.properties.SuhAiderProperties;

import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.ai.service.CategoryMatchingService;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.SystemConfigKeys;
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

  /**
   * DB 전체 설정 > Redis 캐시 + AI 프로퍼티 반영
   * 내부 호출 전용 (트랜잭션은 호출부 reloadCache 기준, 기동 시에는 findAll 단건 트랜잭션)
   */
  private void loadAllToRedis() {
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
    Map<String, String> aiConfigMap = systemConfigCacheService.getByPrefix(SystemConfigKeys.AI_PREFIX);
    return AdminResponse.builder()
        .aiPrimaryProvider(aiConfigMap.get(SystemConfigKeys.AI_PRIMARY_PROVIDER))
        .aiFallbackProvider(aiConfigMap.get(SystemConfigKeys.AI_FALLBACK_PROVIDER))
        .aiOllamaEnabled(aiConfigMap.get(SystemConfigKeys.AI_OLLAMA_ENABLED))
        .aiOllamaBaseUrl(aiConfigMap.get(SystemConfigKeys.AI_OLLAMA_BASE_URL))
        .aiOllamaChatModel(aiConfigMap.get(SystemConfigKeys.AI_OLLAMA_CHAT_MODEL))
        .aiOllamaEmbeddingModel(aiConfigMap.get(SystemConfigKeys.AI_OLLAMA_EMBEDDING_MODEL))
        .aiVertexEnabled(aiConfigMap.get(SystemConfigKeys.AI_VERTEX_ENABLED))
        .aiVertexGenerationModel(aiConfigMap.get(SystemConfigKeys.AI_VERTEX_GENERATION_MODEL))
        .aiVertexEmbeddingModel(aiConfigMap.get(SystemConfigKeys.AI_VERTEX_EMBEDDING_MODEL))
        .aiVertexGenerationLocation(aiConfigMap.get(SystemConfigKeys.AI_VERTEX_GENERATION_LOCATION))
        .aiVertexEmbeddingLocation(aiConfigMap.get(SystemConfigKeys.AI_VERTEX_EMBEDDING_LOCATION))
        .aiPromptPricePredictionInstruction(aiConfigMap.get(SystemConfigKeys.AI_PROMPT_PRICE_PREDICTION_INSTRUCTION))
        .aiPromptChatRecommendationInstruction(aiConfigMap.get(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_INSTRUCTION))
        .aiPromptChatRecommendationEnabled(aiConfigMap.get(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_ENABLED))
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
        case SystemConfigKeys.AI_PRIMARY_PROVIDER -> "AI 기본 프로바이더";
        case SystemConfigKeys.AI_FALLBACK_PROVIDER -> "AI 폴백 프로바이더";
        case SystemConfigKeys.AI_OLLAMA_ENABLED -> "Ollama 활성화 여부";
        case SystemConfigKeys.AI_OLLAMA_BASE_URL -> "Ollama Base URL";
        case SystemConfigKeys.AI_OLLAMA_CHAT_MODEL -> "Ollama Chat 모델";
        case SystemConfigKeys.AI_OLLAMA_EMBEDDING_MODEL -> "Ollama Embedding 모델";
        case SystemConfigKeys.AI_VERTEX_ENABLED -> "Vertex AI 활성화 여부";
        case SystemConfigKeys.AI_VERTEX_GENERATION_MODEL -> "Vertex AI Generation 모델";
        case SystemConfigKeys.AI_VERTEX_EMBEDDING_MODEL -> "Vertex AI Embedding 모델";
        case SystemConfigKeys.AI_VERTEX_GENERATION_LOCATION -> "Vertex AI Generation 위치";
        case SystemConfigKeys.AI_VERTEX_EMBEDDING_LOCATION -> "Vertex AI Embedding 위치";
        case SystemConfigKeys.AI_PROMPT_PRICE_PREDICTION_INSTRUCTION -> "가격 예측 AI System Prompt 본문 ({{INPUT_TEXT}} 치환 템플릿)";
        case SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_INSTRUCTION -> "채팅 추천 AI System Prompt 본문";
        case SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_ENABLED -> "채팅 추천 AI 활성화 여부";
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
    if (adminRequest.getAiPrimaryProvider() != null) aiConfigMap.put(SystemConfigKeys.AI_PRIMARY_PROVIDER, adminRequest.getAiPrimaryProvider());
    if (adminRequest.getAiFallbackProvider() != null) aiConfigMap.put(SystemConfigKeys.AI_FALLBACK_PROVIDER, adminRequest.getAiFallbackProvider());
    if (adminRequest.getAiOllamaEnabled() != null) aiConfigMap.put(SystemConfigKeys.AI_OLLAMA_ENABLED, adminRequest.getAiOllamaEnabled());
    if (adminRequest.getAiOllamaBaseUrl() != null) aiConfigMap.put(SystemConfigKeys.AI_OLLAMA_BASE_URL, adminRequest.getAiOllamaBaseUrl());
    if (adminRequest.getAiOllamaChatModel() != null) aiConfigMap.put(SystemConfigKeys.AI_OLLAMA_CHAT_MODEL, adminRequest.getAiOllamaChatModel());
    if (adminRequest.getAiOllamaEmbeddingModel() != null) aiConfigMap.put(SystemConfigKeys.AI_OLLAMA_EMBEDDING_MODEL, adminRequest.getAiOllamaEmbeddingModel());
    if (adminRequest.getAiVertexEnabled() != null) aiConfigMap.put(SystemConfigKeys.AI_VERTEX_ENABLED, adminRequest.getAiVertexEnabled());
    if (adminRequest.getAiVertexGenerationModel() != null) aiConfigMap.put(SystemConfigKeys.AI_VERTEX_GENERATION_MODEL, adminRequest.getAiVertexGenerationModel());
    if (adminRequest.getAiVertexEmbeddingModel() != null) aiConfigMap.put(SystemConfigKeys.AI_VERTEX_EMBEDDING_MODEL, adminRequest.getAiVertexEmbeddingModel());
    if (adminRequest.getAiVertexGenerationLocation() != null) aiConfigMap.put(SystemConfigKeys.AI_VERTEX_GENERATION_LOCATION, adminRequest.getAiVertexGenerationLocation());
    if (adminRequest.getAiVertexEmbeddingLocation() != null) aiConfigMap.put(SystemConfigKeys.AI_VERTEX_EMBEDDING_LOCATION, adminRequest.getAiVertexEmbeddingLocation());
    if (adminRequest.getAiPromptPricePredictionInstruction() != null) aiConfigMap.put(SystemConfigKeys.AI_PROMPT_PRICE_PREDICTION_INSTRUCTION, adminRequest.getAiPromptPricePredictionInstruction());
    if (adminRequest.getAiPromptChatRecommendationInstruction() != null) aiConfigMap.put(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_INSTRUCTION, adminRequest.getAiPromptChatRecommendationInstruction());
    if (adminRequest.getAiPromptChatRecommendationEnabled() != null) aiConfigMap.put(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_ENABLED, adminRequest.getAiPromptChatRecommendationEnabled());
    return aiConfigMap;
  }

  @Transactional(readOnly = true)
  public void reloadCache() {
    loadAllToRedis();
    log.info("시스템 설정 캐시 리로드 완료");
  }

  public AdminResponse getAppVersionConfig() {
    Map<String, String> appVersionConfigMap = systemConfigCacheService.getByPrefix(SystemConfigKeys.APP_PREFIX);
    return AdminResponse.builder()
        .appLatestVersion(appVersionConfigMap.get(SystemConfigKeys.APP_LATEST_VERSION))
        .appMinVersion(appVersionConfigMap.get(SystemConfigKeys.APP_MIN_VERSION))
        .appStoreAndroid(appVersionConfigMap.get(SystemConfigKeys.APP_STORE_ANDROID))
        .appStoreIos(appVersionConfigMap.get(SystemConfigKeys.APP_STORE_IOS))
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

      if (SystemConfigKeys.APP_MIN_VERSION.equals(configKey)) {
        if (!trimmedConfigValue.matches("^\\d+\\.\\d+\\.\\d+$")) {
          log.warn("앱 버전 형식 오류: {}", trimmedConfigValue);
          throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
      }

      String configDescription = switch (configKey) {
        case SystemConfigKeys.APP_MIN_VERSION -> "앱 최소 필수 버전";
        case SystemConfigKeys.APP_STORE_ANDROID -> "Android Google Play URL";
        case SystemConfigKeys.APP_STORE_IOS -> "iOS App Store URL";
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
    if (adminRequest.getAppMinVersion() != null) appVersionConfigMap.put(SystemConfigKeys.APP_MIN_VERSION, adminRequest.getAppMinVersion());
    if (adminRequest.getAppStoreAndroid() != null) appVersionConfigMap.put(SystemConfigKeys.APP_STORE_ANDROID, adminRequest.getAppStoreAndroid());
    if (adminRequest.getAppStoreIos() != null) appVersionConfigMap.put(SystemConfigKeys.APP_STORE_IOS, adminRequest.getAppStoreIos());
    return appVersionConfigMap;
  }

  /**
   * 서버 점검 모드 설정 조회
   * Redis 캐시에서 점검 관련 3개 키를 읽어 AdminResponse로 반환
   */
  public AdminResponse getMaintenanceConfig() {
    return AdminResponse.builder()
        .maintenanceEnabled(systemConfigCacheService.getOrDefault(SystemConfigKeys.SERVER_MAINTENANCE_ENABLED, "false"))
        .maintenanceMessage(systemConfigCacheService.getOrDefault(SystemConfigKeys.SERVER_MAINTENANCE_MESSAGE, ""))
        .maintenanceEndTime(systemConfigCacheService.getOrDefault(SystemConfigKeys.SERVER_MAINTENANCE_END_TIME, ""))
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
      upsertConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_ENABLED, enabledValue, "서버 점검 모드 활성화 여부");
    }

    if (adminRequest.getMaintenanceMessage() != null) {
      upsertConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_MESSAGE, adminRequest.getMaintenanceMessage().trim(), "서버 점검 안내 메시지");
    }

    if (adminRequest.getMaintenanceEndTime() != null) {
      upsertConfigValue(SystemConfigKeys.SERVER_MAINTENANCE_END_TIME, adminRequest.getMaintenanceEndTime().trim(), "서버 점검 예상 종료 시간");
    }

    log.info("서버 점검 모드 설정 업데이트 완료: enabled={}", adminRequest.getMaintenanceEnabled());
    return getMaintenanceConfig();
  }

  /**
   * 설정 단건 upsert: DB 저장 + Redis 캐시 동기화 (없으면 설명과 함께 생성)
   */
  @Transactional
  public void upsertConfigValue(String configKey, String configValue, String description) {
    SystemConfig maintenanceConfig = systemConfigRepository.findByConfigKey(configKey)
        .orElseGet(() -> SystemConfig.builder().configKey(configKey).description(description).build());
    maintenanceConfig.setConfigValue(configValue);
    systemConfigRepository.save(maintenanceConfig);
    systemConfigCacheService.put(configKey, configValue);
  }

  public AdminResponse getUgcFilterConfig() {
    String ugcPatternJson = systemConfigCacheService.getOrDefault(SystemConfigKeys.UGC_FILTER_PATTERNS, "[]");
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
    SystemConfig ugcFilterConfig = systemConfigRepository.findByConfigKey(SystemConfigKeys.UGC_FILTER_PATTERNS)
        .orElseGet(() -> SystemConfig.builder()
            .configKey(SystemConfigKeys.UGC_FILTER_PATTERNS)
            .description("UGC 텍스트 필터링 정규식 패턴 목록 (JSON 배열)")
            .build());
    ugcFilterConfig.setConfigValue(newUgcPatternsJson);
    systemConfigRepository.save(ugcFilterConfig);

    // Redis 캐시 업데이트
    systemConfigCacheService.put(SystemConfigKeys.UGC_FILTER_PATTERNS, newUgcPatternsJson);

    // UgcFilterService 인메모리 패턴 캐시 무효화
    ugcFilterService.invalidateCompiledPatternCache();

    log.info("UGC 필터 패턴 업데이트 완료: {} 개 패턴", newUgcPatternStrings.size());
    return getUgcFilterConfig();
  }

  public AdminResponse getImageConfig() {
    return AdminResponse.builder()
        .imageCompressSkipContentType(systemConfigCacheService.getOrDefault(SystemConfigKeys.IMAGE_COMPRESS_SKIP_CONTENT_TYPE, "image/webp"))
        .imageCompressSkipMaxSizeBytes(systemConfigCacheService.getOrDefault(SystemConfigKeys.IMAGE_COMPRESS_SKIP_MAX_SIZE_BYTES, "512000"))
        .imageUploadParallelPoolSize(systemConfigCacheService.getOrDefault(SystemConfigKeys.IMAGE_UPLOAD_PARALLEL_POOL_SIZE, "8"))
        .build();
  }

  /**
   * 이미지 압축/업로드 설정 업데이트 (PATCH 방식)
   * contentType/용량은 Redis 즉시 반영, 풀 크기는 서버 재시작 시 반영
   */
  @Transactional
  public AdminResponse updateImageConfig(AdminRequest adminRequest) {
    if (adminRequest.getImageCompressSkipContentType() != null) {
      String skipContentTypeValue = adminRequest.getImageCompressSkipContentType().trim();
      if (!skipContentTypeValue.isEmpty()) {
        upsertConfigValue(SystemConfigKeys.IMAGE_COMPRESS_SKIP_CONTENT_TYPE, skipContentTypeValue, "압축 스킵 대상 contentType");
      }
    }
    if (adminRequest.getImageCompressSkipMaxSizeBytes() != null) {
      String skipMaxSizeValue = adminRequest.getImageCompressSkipMaxSizeBytes().trim();
      if (!skipMaxSizeValue.isEmpty()) {
        validateNonNegativeLong(skipMaxSizeValue);
        upsertConfigValue(SystemConfigKeys.IMAGE_COMPRESS_SKIP_MAX_SIZE_BYTES, skipMaxSizeValue, "압축 스킵 최대 용량(byte)");
      }
    }
    if (adminRequest.getImageUploadParallelPoolSize() != null) {
      String poolSizeValue = adminRequest.getImageUploadParallelPoolSize().trim();
      if (!poolSizeValue.isEmpty()) {
        validatePositiveInt(poolSizeValue);
        upsertConfigValue(SystemConfigKeys.IMAGE_UPLOAD_PARALLEL_POOL_SIZE, poolSizeValue, "이미지 업로드 병렬 스레드풀 크기 (서버 재시작 시 반영)");
      }
    }
    log.info("이미지 압축/업로드 설정 업데이트 완료");
    return getImageConfig();
  }

  private void validateNonNegativeLong(String numericValue) {
    try {
      if (Long.parseLong(numericValue) < 0) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } catch (NumberFormatException e) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private void validatePositiveInt(String numericValue) {
    try {
      if (Integer.parseInt(numericValue) <= 0) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } catch (NumberFormatException e) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private void applyToProperties(Map<String, String> configMap) {
    if (configMap.containsKey(SystemConfigKeys.AI_OLLAMA_BASE_URL)) {
      suhAiderProperties.setBaseUrl(configMap.get(SystemConfigKeys.AI_OLLAMA_BASE_URL));
    }
    if (configMap.containsKey(SystemConfigKeys.AI_OLLAMA_EMBEDDING_MODEL)) {
      if (suhAiderProperties.getEmbedding() != null) {
        suhAiderProperties.getEmbedding().setDefaultModel(configMap.get(SystemConfigKeys.AI_OLLAMA_EMBEDDING_MODEL));
      }
    }
    if (configMap.containsKey(SystemConfigKeys.AI_VERTEX_GENERATION_MODEL)) {
      vertexAiProperties.setGenerationModel(configMap.get(SystemConfigKeys.AI_VERTEX_GENERATION_MODEL));
    }
    if (configMap.containsKey(SystemConfigKeys.AI_VERTEX_EMBEDDING_MODEL)) {
      vertexAiProperties.setEmbeddingModel(configMap.get(SystemConfigKeys.AI_VERTEX_EMBEDDING_MODEL));
    }
    if (configMap.containsKey(SystemConfigKeys.AI_VERTEX_EMBEDDING_LOCATION)) {
      vertexAiProperties.setEmbeddingLocation(configMap.get(SystemConfigKeys.AI_VERTEX_EMBEDDING_LOCATION));
    }
    if (configMap.containsKey(SystemConfigKeys.AI_VERTEX_GENERATION_LOCATION)) {
      vertexAiProperties.setGenerationLocation(configMap.get(SystemConfigKeys.AI_VERTEX_GENERATION_LOCATION));
    }
  }
}
