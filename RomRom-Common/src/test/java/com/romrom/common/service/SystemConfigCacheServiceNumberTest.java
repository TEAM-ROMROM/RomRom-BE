package com.romrom.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 양의 정수 설정 조회 검증 (#815): 정상 값, 없음·빈 값·숫자 아님·0 이하 > 기본값
 */
class SystemConfigCacheServiceNumberTest {

  private static final String CONFIG_KEY = "image.upload.parallel-pool-size";
  private static final int DEFAULT_VALUE = 8;

  @SuppressWarnings("unchecked")
  private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);

  private SystemConfigCacheService serviceReturning(Object cachedConfigValue) {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    when(redisTemplate.opsForHash()).thenReturn((HashOperations) hashOperations);
    when(hashOperations.get("system:config", CONFIG_KEY)).thenReturn(cachedConfigValue);
    return new SystemConfigCacheService(redisTemplate);
  }

  @Test
  @DisplayName("양의 정수는 그대로 (앞뒤 공백 허용)")
  void 양의_정수는_그대로_반환한다() {
    assertEquals(12, serviceReturning(" 12 ").getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
  }

  @Test
  @DisplayName("없음·빈 값·숫자 아님·0 이하는 기본값")
  void 잘못된_값은_기본값을_반환한다() {
    assertEquals(DEFAULT_VALUE, serviceReturning(null).getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
    assertEquals(DEFAULT_VALUE, serviceReturning("  ").getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
    assertEquals(DEFAULT_VALUE, serviceReturning("eight").getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
    assertEquals(DEFAULT_VALUE, serviceReturning("0").getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
    assertEquals(DEFAULT_VALUE, serviceReturning("-3").getPositiveIntOrDefault(CONFIG_KEY, DEFAULT_VALUE));
  }
}
