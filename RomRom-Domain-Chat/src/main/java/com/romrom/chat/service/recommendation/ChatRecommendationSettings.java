package com.romrom.chat.service.recommendation;

import com.romrom.chat.constant.ChatRecommendationConstants;
import com.romrom.chat.properties.ChatRecommendationPolicyProperties;
import com.romrom.chat.properties.ChatRecommendationPromptProperties;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.service.SystemConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 채팅 추천 설정 조회: 활성화 여부, instruction, 모델, 쿨다운·TTL, 프롬프트 한도
 * system_config(Redis 캐시) 우선 > yml(ChatRecommendation*Properties) 기본값 fallback
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRecommendationSettings {

  // yml 값이 1 미만일 때 쓰는 기본값
  private static final int FALLBACK_RECENT_MESSAGE_LIMIT = 6;
  private static final int FALLBACK_MAX_PROMPT_MESSAGE_LENGTH = 120;
  private static final int FALLBACK_TRIVIAL_MESSAGE_LENGTH = 2;
  private static final int FALLBACK_MAX_OUTPUT_TOKENS = 24;
  private static final double FALLBACK_TEMPERATURE = 0.0d;

  private final SystemConfigCacheService systemConfigCacheService;
  private final ChatRecommendationPolicyProperties policyProperties;
  private final ChatRecommendationPromptProperties promptProperties;

  /**
   * 추천 기능 활성화 여부: 활성화 토글 켜짐 + instruction 존재
   */
  public boolean isRecommendationEnabled() {
    return isRecommendationToggleEnabled() && StringUtils.hasText(resolveRecommendationInstruction());
  }

  /**
   * 시스템 instruction: 관리자 지정값 > yml 기본값
   */
  public String resolveRecommendationInstruction() {
    String overriddenInstruction = systemConfigCacheService.get(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_INSTRUCTION);
    if (overriddenInstruction != null && !overriddenInstruction.isBlank()) {
      return overriddenInstruction;
    }
    return promptProperties.getInstruction();
  }

  /**
   * 추천 모델명: yml 전용 모델 > system_config 모델 > 기본 모델
   */
  public String resolveRecommendationModel() {
    if (StringUtils.hasText(promptProperties.getModel())) {
      return promptProperties.getModel();
    }

    String configuredModel = systemConfigCacheService.get(SystemConfigKeys.CHAT_RECOMMENDATION_MODEL);
    if (StringUtils.hasText(configuredModel)) {
      return configuredModel;
    }

    return ChatRecommendationConstants.DEFAULT_CHAT_MODEL;
  }

  // 운영 중 system_config 로 조정 가능한 초 단위 설정

  public int getLlmCooldownSeconds() {
    return getPositiveIntConfig(SystemConfigKeys.CHAT_RECOMMENDATION_LLM_COOLDOWN_SECONDS, policyProperties.getLlmCooldownSeconds());
  }

  public int getSameActionCooldownSeconds() {
    return getPositiveIntConfig(
        SystemConfigKeys.CHAT_RECOMMENDATION_SAME_ACTION_COOLDOWN_SECONDS,
        policyProperties.getSameActionCooldownSeconds()
    );
  }

  public int getCacheTtlSeconds() {
    return getPositiveIntConfig(SystemConfigKeys.CHAT_RECOMMENDATION_CACHE_TTL_SECONDS, policyProperties.getCacheTtlSeconds());
  }

  public int getTradeCompletionRetryCooldownSeconds() {
    return getPositiveIntConfig(
        SystemConfigKeys.CHAT_RECOMMENDATION_TRADE_COMPLETION_RETRY_COOLDOWN_SECONDS,
        policyProperties.getTradeCompletionRetryCooldownSeconds()
    );
  }

  public int getTradeCompletionInactivitySeconds() {
    return getPositiveIntConfig(
        SystemConfigKeys.CHAT_RECOMMENDATION_TRADE_COMPLETION_INACTIVITY_SECONDS,
        policyProperties.getTradeCompletionInactivitySeconds()
    );
  }

  // yml 전용 설정 (1 미만이면 기본값)

  public int getRecentMessageLimit() {
    return getPositiveProperty(
        "ai.chat.recommendation.prompt.recent-message-limit",
        promptProperties.getRecentMessageLimit(),
        FALLBACK_RECENT_MESSAGE_LIMIT
    );
  }

  public int getMaxPromptMessageLength() {
    return getPositiveProperty(
        "ai.chat.recommendation.max-prompt-message-length",
        policyProperties.getMaxPromptMessageLength(),
        FALLBACK_MAX_PROMPT_MESSAGE_LENGTH
    );
  }

  public int getTrivialMessageLength() {
    return getPositiveProperty(
        "ai.chat.recommendation.trivial-message-length",
        policyProperties.getTrivialMessageLength(),
        FALLBACK_TRIVIAL_MESSAGE_LENGTH
    );
  }

  public int getMaxOutputTokens() {
    return getPositiveProperty(
        "ai.chat.recommendation.prompt.generation-config.max-output-tokens",
        promptProperties.getGenerationConfig().getMaxOutputTokens(),
        FALLBACK_MAX_OUTPUT_TOKENS
    );
  }

  /**
   * 생성 temperature (0 미만이면 0.0)
   */
  public double getTemperature() {
    double temperature = promptProperties.getGenerationConfig().getTemperature();
    if (temperature < 0d) {
      log.warn("채팅 추천 temperature 설정이 0 미만이라 기본값을 사용합니다. value={}, default={}", temperature, FALLBACK_TEMPERATURE);
      return FALLBACK_TEMPERATURE;
    }
    return temperature;
  }

  // 활성화 토글: 관리자 지정값 > yml enabled
  private boolean isRecommendationToggleEnabled() {
    String overriddenEnabled = systemConfigCacheService.get(SystemConfigKeys.CHAT_RECOMMENDATION_PROMPT_ENABLED);
    if (overriddenEnabled == null || overriddenEnabled.isBlank()) {
      return promptProperties.isEnabled();
    }
    return Boolean.parseBoolean(overriddenEnabled.trim());
  }

  // system_config 양의 정수 우선, 없거나 잘못되면 yml 기본값(1 미만이면 1로 보정)
  private int getPositiveIntConfig(String configKey, int defaultValue) {
    return systemConfigCacheService.getPositiveIntOrDefault(configKey, getPositiveDefault(configKey, defaultValue));
  }

  private int getPositiveProperty(String propertyName, int configuredValue, int fallbackValue) {
    if (configuredValue < 1) {
      int safeFallbackValue = Math.max(1, fallbackValue);
      log.warn("채팅 추천 로컬 설정이 1 미만이라 기본값을 사용합니다. key={}, value={}, default={}",
          propertyName, configuredValue, safeFallbackValue);
      return safeFallbackValue;
    }
    return configuredValue;
  }

  private int getPositiveDefault(String configKey, int defaultValue) {
    int safeDefaultValue = Math.max(1, defaultValue);
    if (safeDefaultValue != defaultValue) {
      log.warn("채팅 추천 기본 설정도 1 미만이라 1로 보정합니다. key={}, default={}", configKey, defaultValue);
    }
    return safeDefaultValue;
  }
}
