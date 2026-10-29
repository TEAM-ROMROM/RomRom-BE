package com.romrom.chat.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.chat.constant.ChatRecommendationConstants;
import com.romrom.chat.dto.ChatActionRecommendationPayload;
import com.romrom.chat.dto.ChatRecommendedAction;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 추천 Redis 저장소: 추천 결과 캐시, LLM 호출 쿨다운, 같은 액션 반복 추천 쿨다운
 * 키 3종: CACHE:{방}:{사용자}, LLM:{방}:{사용자}, ACTION:{방}:{사용자}:{액션}
 */
@Component
@RequiredArgsConstructor
public class ChatRecommendationCooldownStore {

  // 쿨다운 점유 표시값
  private static final String COOLDOWN_MARKER_VALUE = "1";
  private static final String KEY_DELIMITER = ":";

  private final RedisTemplate<String, Object> redisTemplate;
  private final ObjectMapper objectMapper;
  private final ChatRecommendationSettings settings;

  /**
   * 같은 마지막 메시지 기준 캐시 추천 조회 (방·사용자·기준 메시지 불일치 시 null)
   */
  public ChatActionRecommendationPayload getCachedRecommendation(UUID chatRoomId, UUID viewerId, String basedOnMessageId) {
    Object cachedValue = redisTemplate.opsForValue().get(buildCacheKey(chatRoomId, viewerId));
    if (cachedValue == null) {
      return null;
    }

    ChatActionRecommendationPayload cachedPayload = cachedValue instanceof ChatActionRecommendationPayload payload
        ? payload
        : objectMapper.convertValue(cachedValue, ChatActionRecommendationPayload.class);

    if (cachedPayload == null) {
      return null;
    }
    if (!chatRoomId.equals(cachedPayload.getChatRoomId()) || !viewerId.equals(cachedPayload.getTargetMemberId())) {
      return null;
    }
    if (basedOnMessageId == null) {
      return cachedPayload.getBasedOnMessageId() == null ? cachedPayload : null;
    }
    return basedOnMessageId.equals(cachedPayload.getBasedOnMessageId()) ? cachedPayload : null;
  }

  /**
   * 추천 결과 캐시 저장 (기본 TTL)
   */
  public ChatActionRecommendationPayload cacheRecommendation(ChatActionRecommendationPayload payload) {
    return cacheRecommendation(payload, settings.getCacheTtlSeconds());
  }

  /**
   * 추천 결과 캐시 저장 (지정 TTL, 최소 1초)
   */
  public ChatActionRecommendationPayload cacheRecommendation(ChatActionRecommendationPayload payload, int ttlSeconds) {
    int effectiveTtlSeconds = Math.max(1, ttlSeconds);
    redisTemplate.opsForValue().set(
        buildCacheKey(payload.getChatRoomId(), payload.getTargetMemberId()),
        payload,
        effectiveTtlSeconds,
        TimeUnit.SECONDS
    );
    return payload;
  }

  /**
   * 방·사용자별 LLM 호출 쿨다운 점유 시도 (이미 점유 중이면 false)
   */
  public boolean tryAcquireLlmCooldown(UUID chatRoomId, UUID viewerId) {
    Boolean isAcquired = redisTemplate.opsForValue().setIfAbsent(
        buildLlmCooldownKey(chatRoomId, viewerId),
        COOLDOWN_MARKER_VALUE,
        settings.getLlmCooldownSeconds(),
        TimeUnit.SECONDS
    );
    return !Boolean.FALSE.equals(isAcquired);
  }

  /**
   * LLM 호출 쿨다운 진행 여부 (점유하지 않고 조회만)
   */
  public boolean isLlmCooldownActive(UUID chatRoomId, UUID viewerId) {
    return Boolean.TRUE.equals(redisTemplate.hasKey(buildLlmCooldownKey(chatRoomId, viewerId)));
  }

  /**
   * 같은 액션 반복 추천 쿨다운 점유 시도 (이미 쿨다운 중이면 false, NONE 은 점유 없이 항상 true)
   */
  public boolean tryAcquireSameActionCooldown(UUID chatRoomId, UUID viewerId, ChatRecommendedAction action) {
    if (action == null || action.isNone()) {
      return true;
    }
    Boolean isAcquired = redisTemplate.opsForValue().setIfAbsent(
        buildActionCooldownKey(chatRoomId, viewerId, action),
        COOLDOWN_MARKER_VALUE,
        settings.getSameActionCooldownSeconds(),
        TimeUnit.SECONDS
    );
    return !Boolean.FALSE.equals(isAcquired);
  }

  // 키: CHAT:RECOMMEND:CACHE:{chatRoomId}:{viewerId}
  private String buildCacheKey(UUID chatRoomId, UUID viewerId) {
    return ChatRecommendationConstants.CACHE_KEY_PREFIX + chatRoomId + KEY_DELIMITER + viewerId;
  }

  // 키: CHAT:RECOMMEND:LLM:{chatRoomId}:{viewerId}
  private String buildLlmCooldownKey(UUID chatRoomId, UUID viewerId) {
    return ChatRecommendationConstants.LLM_COOLDOWN_KEY_PREFIX + chatRoomId + KEY_DELIMITER + viewerId;
  }

  // 키: CHAT:RECOMMEND:ACTION:{chatRoomId}:{viewerId}:{action}
  private String buildActionCooldownKey(UUID chatRoomId, UUID viewerId, ChatRecommendedAction action) {
    return ChatRecommendationConstants.ACTION_COOLDOWN_KEY_PREFIX + chatRoomId + KEY_DELIMITER + viewerId
        + KEY_DELIMITER + action.name();
  }
}
