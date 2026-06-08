package com.romrom.common.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * 앱 전체 동접(온라인 사용자) 집계 서비스.
 *
 * <p>모바일 앱은 HTTP 요청이 stateless라 "현재 연결 여부"를 직접 알 수 없으므로,
 * production 표준인 Last-Seen + 시간 윈도우 근사를 사용한다.
 * Redis Sorted Set에 (score=마지막 활동 시각 epoch millis, member=memberId)를 기록하고,
 * 조회 시 윈도우(5분)를 벗어난 멤버를 청소한 뒤 남은 멤버 수를 동접자로 본다.
 *
 * <p>모든 인스턴스가 같은 Redis를 공유하므로 블루그린 등 다중 인스턴스에서도 합산이 정확하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnlinePresenceService {

  // 모든 인스턴스가 공유하는 단일 Sorted Set 키
  private static final String ONLINE_PRESENCE_KEY = "online:members";

  // 마지막 활동 후 이 시간 이내면 온라인으로 간주 (5분)
  private static final long ONLINE_WINDOW_MILLIS = 5 * 60 * 1000L;

  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * heartbeat 기록: 해당 회원의 마지막 활동 시각을 현재로 갱신한다.
   * 인증된 모든 API 요청에서 호출되므로, 호출 측에서 예외를 격리해 본 요청 흐름을 막지 않아야 한다.
   *
   * @param memberId     활동한 회원 ID
   * @param nowEpochMilli 현재 시각 (epoch millis) — 호출 측에서 주입해 테스트 가능성 확보
   */
  public void recordHeartbeat(UUID memberId, long nowEpochMilli) {
    if (memberId == null) {
      return;
    }
    // 같은 member로 ZADD 하면 score(활동 시각)만 최신으로 덮어쓴다 → 중복 없이 last-seen 유지
    redisTemplate.opsForZSet().add(ONLINE_PRESENCE_KEY, memberId.toString(), nowEpochMilli);
  }

  /**
   * 현재 동접자 수 조회.
   * 윈도우를 벗어난(stale) 멤버를 먼저 청소한 뒤 남은 전체 수를 반환한다.
   *
   * @param nowEpochMilli 현재 시각 (epoch millis)
   * @return 최근 {@code ONLINE_WINDOW_MILLIS} 이내 활동한 고유 회원 수
   */
  public long countOnlineMembers(long nowEpochMilli) {
    long onlineThreshold = nowEpochMilli - ONLINE_WINDOW_MILLIS;

    ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
    // 윈도우를 벗어난 멤버 청소: score가 [0, threshold) 범위면 제거
    // (removeRangeByScore는 inclusive 경계이므로 stale 상한은 threshold 직전 값으로 둔다)
    zSetOperations.removeRangeByScore(ONLINE_PRESENCE_KEY, 0, (double) onlineThreshold - 1);

    Long onlineMemberCount = zSetOperations.zCard(ONLINE_PRESENCE_KEY);
    return onlineMemberCount == null ? 0L : onlineMemberCount;
  }
}
