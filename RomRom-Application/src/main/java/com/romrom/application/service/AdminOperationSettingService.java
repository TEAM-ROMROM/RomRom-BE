package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.service.recommendation.ChatRecommendationSettings;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.entity.postgres.SystemConfig;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.SystemConfigRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 운영 설정: 채팅 추천 운영값 조회·수정, 전체 시스템 설정 조회(비밀 값 가림)
 * 저장은 SystemConfigService.upsertConfigValue (DB + Redis 캐시 동시 반영)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminOperationSettingService {

  // 화면에 값을 그대로 보이지 않는 설정 키
  private static final Set<String> SECRET_CONFIG_KEYS = Set.of(SystemConfigKeys.MAIL_SMTP_PASSWORD);
  private static final String MASKED_CONFIG_VALUE = "******";

  private final SystemConfigRepository systemConfigRepository;
  private final SystemConfigService systemConfigService;
  private final ChatRecommendationSettings chatRecommendationSettings;

  /**
   * 채팅 추천 운영값 (적용 중인 값: system_config > yml 기본값)
   */
  public AdminResponse getChatRecommendationConfig() {
    return AdminResponse.builder()
        .chatRecommendationModel(chatRecommendationSettings.resolveRecommendationModel())
        .chatRecommendationLlmCooldownSeconds(chatRecommendationSettings.getLlmCooldownSeconds())
        .chatRecommendationSameActionCooldownSeconds(chatRecommendationSettings.getSameActionCooldownSeconds())
        .chatRecommendationCacheTtlSeconds(chatRecommendationSettings.getCacheTtlSeconds())
        .chatRecommendationTradeCompletionInactivitySeconds(chatRecommendationSettings.getTradeCompletionInactivitySeconds())
        .chatRecommendationTradeCompletionRetryCooldownSeconds(chatRecommendationSettings.getTradeCompletionRetryCooldownSeconds())
        .build();
  }

  /**
   * 채팅 추천 운영값 수정 (보낸 값만, 숫자는 양수 초)
   */
  @Transactional
  public AdminResponse updateChatRecommendationConfig(AdminRequest request) {
    if (StringUtils.hasText(request.getChatRecommendationModel())) {
      systemConfigService.upsertConfigValue(SystemConfigKeys.CHAT_RECOMMENDATION_MODEL,
          request.getChatRecommendationModel().trim(), "채팅 추천 모델");
    }
    upsertPositiveSeconds(SystemConfigKeys.CHAT_RECOMMENDATION_LLM_COOLDOWN_SECONDS,
        request.getChatRecommendationLlmCooldownSeconds(), "채팅 추천 LLM 호출 쿨다운(초)");
    upsertPositiveSeconds(SystemConfigKeys.CHAT_RECOMMENDATION_SAME_ACTION_COOLDOWN_SECONDS,
        request.getChatRecommendationSameActionCooldownSeconds(), "채팅 추천 같은 동작 쿨다운(초)");
    upsertPositiveSeconds(SystemConfigKeys.CHAT_RECOMMENDATION_CACHE_TTL_SECONDS,
        request.getChatRecommendationCacheTtlSeconds(), "채팅 추천 캐시 유지(초)");
    upsertPositiveSeconds(SystemConfigKeys.CHAT_RECOMMENDATION_TRADE_COMPLETION_INACTIVITY_SECONDS,
        request.getChatRecommendationTradeCompletionInactivitySeconds(), "거래 완료 제안까지 대화 없는 시간(초)");
    upsertPositiveSeconds(SystemConfigKeys.CHAT_RECOMMENDATION_TRADE_COMPLETION_RETRY_COOLDOWN_SECONDS,
        request.getChatRecommendationTradeCompletionRetryCooldownSeconds(), "거래 완료 제안 재시도 쿨다운(초)");
    log.info("[admin setting] chat recommendation config updated");
    return getChatRecommendationConfig();
  }

  /**
   * 전체 시스템 설정 (키 순, 비밀 값은 가린 사본)
   */
  @Transactional(readOnly = true)
  public AdminResponse getAllSystemConfigs() {
    List<SystemConfig> maskedSystemConfigs = systemConfigRepository.findAll().stream()
        .sorted(Comparator.comparing(SystemConfig::getConfigKey))
        .map(this::toDisplaySystemConfig)
        .toList();
    return AdminResponse.builder().systemConfigs(maskedSystemConfigs).build();
  }

  // 화면 표시용 사본 (영속 엔티티를 바꾸지 않음)
  private SystemConfig toDisplaySystemConfig(SystemConfig systemConfig) {
    boolean isSecretConfig = SECRET_CONFIG_KEYS.contains(systemConfig.getConfigKey());
    String displayValue = isSecretConfig && StringUtils.hasText(systemConfig.getConfigValue())
        ? MASKED_CONFIG_VALUE
        : systemConfig.getConfigValue();
    SystemConfig displaySystemConfig = SystemConfig.builder()
        .systemConfigId(systemConfig.getSystemConfigId())
        .configKey(systemConfig.getConfigKey())
        .configValue(displayValue)
        .description(systemConfig.getDescription())
        .build();
    displaySystemConfig.setUpdatedDate(systemConfig.getUpdatedDate());
    return displaySystemConfig;
  }

  private void upsertPositiveSeconds(String configKey, String requestedSeconds, String description) {
    if (!StringUtils.hasText(requestedSeconds)) {
      return;
    }
    String trimmedSeconds = requestedSeconds.trim();
    try {
      if (Integer.parseInt(trimmedSeconds) <= 0) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
    } catch (NumberFormatException numberFormatException) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    systemConfigService.upsertConfigValue(configKey, trimmedSeconds, description);
  }
}
