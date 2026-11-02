package com.romrom.common.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RequestRateLimitServiceTest {

  private static final int MAXIMUM_REQUEST_COUNT = 10;

  private StringRedisTemplate stringRedisTemplate;
  private RequestRateLimitService requestRateLimitService;

  @BeforeEach
  void setUp() {
    stringRedisTemplate = mock(StringRedisTemplate.class);
    requestRateLimitService = new RequestRateLimitService(stringRedisTemplate);
  }

  private void givenRedisRequestCount(long requestCount) {
    when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(requestCount);
  }

  private boolean isRequestAllowed() {
    return requestRateLimitService.isRequestAllowed("admin-login", "2.2.2.2", MAXIMUM_REQUEST_COUNT, Duration.ofMinutes(1));
  }

  @Test
  @DisplayName("상한과 같은 횟수까지는 허용한다")
  void 상한까지_허용한다() {
    givenRedisRequestCount(MAXIMUM_REQUEST_COUNT);
    assertTrue(isRequestAllowed());
  }

  @Test
  @DisplayName("상한을 넘으면 거부한다")
  void 상한을_넘으면_거부한다() {
    givenRedisRequestCount(MAXIMUM_REQUEST_COUNT + 1L);
    assertFalse(isRequestAllowed());
  }

  @Test
  @DisplayName("Redis 장애 시 로그인이 막히지 않도록 허용한다")
  void Redis_장애_시_허용한다() {
    when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .thenThrow(new RedisConnectionFailureException("connection refused"));
    assertTrue(isRequestAllowed());
  }
}
