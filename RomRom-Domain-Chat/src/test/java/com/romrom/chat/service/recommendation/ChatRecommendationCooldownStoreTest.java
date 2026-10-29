package com.romrom.chat.service.recommendation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.chat.dto.ChatRecommendedAction;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 같은 액션 쿨다운 점유 검증 (#816): 점유 성공 true, 이미 쿨다운 false, NONE은 Redis 없이 true
 */
class ChatRecommendationCooldownStoreTest {

  private static final int SAME_ACTION_COOLDOWN_SECONDS = 600;

  @SuppressWarnings("unchecked")
  private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
  @SuppressWarnings("unchecked")
  private final ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
  private final ChatRecommendationSettings settings = mock(ChatRecommendationSettings.class);
  private final ChatRecommendationCooldownStore cooldownStore =
      new ChatRecommendationCooldownStore(redisTemplate, new ObjectMapper(), settings);

  private final UUID chatRoomId = UUID.randomUUID();
  private final UUID viewerId = UUID.randomUUID();

  @Test
  @DisplayName("쿨다운 키를 새로 잡으면 true (추천 진행)")
  void 쿨다운_점유_성공은_true다() {
    givenSetIfAbsentReturns(true);
    assertTrue(cooldownStore.tryAcquireSameActionCooldown(chatRoomId, viewerId, ChatRecommendedAction.SEND_LOCATION));
  }

  @Test
  @DisplayName("이미 쿨다운 중이면 false (같은 액션 반복 추천 차단)")
  void 이미_쿨다운이면_false다() {
    givenSetIfAbsentReturns(false);
    assertFalse(cooldownStore.tryAcquireSameActionCooldown(chatRoomId, viewerId, ChatRecommendedAction.SEND_LOCATION));
  }

  @Test
  @DisplayName("NONE 액션은 쿨다운 대상 아님 (Redis 미호출, true)")
  void NONE_액션은_쿨다운을_잡지_않는다() {
    assertTrue(cooldownStore.tryAcquireSameActionCooldown(chatRoomId, viewerId, ChatRecommendedAction.NONE));
    verifyNoInteractions(redisTemplate);
  }

  private void givenSetIfAbsentReturns(Boolean isAcquired) {
    when(settings.getSameActionCooldownSeconds()).thenReturn(SAME_ACTION_COOLDOWN_SECONDS);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(isAcquired);
  }
}
