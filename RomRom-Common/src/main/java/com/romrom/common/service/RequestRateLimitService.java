package com.romrom.common.service;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 호출 횟수 제한: Redis 고정 구간 카운터 (블루·그린 컨테이너 공유)
 * Redis 장애 시 통과 > 제한 기능 때문에 로그인이 막히지 않게 함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestRateLimitService {

  // Redis 키: rate-limit:{규칙 이름}:{IP 또는 회원 ID}
  private static final String RATE_LIMIT_KEY_PREFIX = "rate-limit:";

  // 증가와 첫 만료 설정을 원자 처리 (중간 장애 시 만료 없는 키 방지)
  private static final DefaultRedisScript<Long> INCREMENT_WITHIN_WINDOW_SCRIPT = new DefaultRedisScript<>("""
      local requestCount = redis.call('INCR', KEYS[1])
      if requestCount == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[1])
      end
      return requestCount
      """, Long.class);

  private final StringRedisTemplate stringRedisTemplate;

  /**
   * 구간 내 요청 수 증가 > 상한 이내면 허용
   */
  public boolean isRequestAllowed(String ruleName, String subject, int maximumRequestCount, Duration window) {
    String rateLimitKey = RATE_LIMIT_KEY_PREFIX + ruleName + ":" + subject;
    try {
      Long requestCount = stringRedisTemplate.execute(
          INCREMENT_WITHIN_WINDOW_SCRIPT,
          List.of(rateLimitKey),
          String.valueOf(window.toSeconds()));
      return requestCount == null || requestCount <= maximumRequestCount;
    } catch (DataAccessException redisException) {
      log.warn("[rate limit] allowed without check : rule={} cause={}", ruleName, redisException.getClass().getSimpleName());
      return true;
    }
  }
}
