package com.romrom.common.service;

import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.timeLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.web.RomBackApplication;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * OnlinePresenceService 통합 테스트 (실제 Redis 연결).
 * heartbeat 기록 → 동접 카운트 → 윈도우 만료 청소 동작을 검증한다.
 */
@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@Slf4j
class OnlinePresenceServiceTest {

  private static final String ONLINE_PRESENCE_KEY = "online:members";
  private static final long ONLINE_WINDOW_MILLIS = 5 * 60 * 1000L;

  @Autowired
  OnlinePresenceService onlinePresenceService;

  @Autowired
  RedisTemplate<String, Object> redisTemplate;

  @Test
  void mainTest() {
    lineLog("테스트시작");

    lineLog(null);
    timeLog(this::heartbeat_기록후_동접카운트_증가_테스트);
    lineLog(null);
    timeLog(this::동일회원_여러번_heartbeat시_카운트_1_테스트);
    lineLog(null);
    timeLog(this::윈도우초과_회원은_조회시_청소되어_제외_테스트);
    lineLog(null);

    lineLog("테스트종료");
  }

  /**
   * 각 시나리오는 테스트 키를 직접 정리한 뒤 시작해, 운영/다른 테스트 데이터에 영향받지 않게 한다.
   */
  private void clearPresenceKey() {
    redisTemplate.delete(ONLINE_PRESENCE_KEY);
  }

  void heartbeat_기록후_동접카운트_증가_테스트() {
    clearPresenceKey();
    long now = System.currentTimeMillis();

    UUID memberId = UUID.randomUUID();
    onlinePresenceService.recordHeartbeat(memberId, now);

    long onlineMemberCount = onlinePresenceService.countOnlineMembers(now);
    superLog(onlineMemberCount);
    assertEquals(1L, onlineMemberCount, "heartbeat 1건 기록 후 동접자는 1명이어야 한다");
  }

  void 동일회원_여러번_heartbeat시_카운트_1_테스트() {
    clearPresenceKey();
    long now = System.currentTimeMillis();

    UUID memberId = UUID.randomUUID();
    // 같은 회원이 여러 요청을 보내도 동접자는 1명 (Sorted Set member 중복 없음)
    onlinePresenceService.recordHeartbeat(memberId, now);
    onlinePresenceService.recordHeartbeat(memberId, now + 1000);
    onlinePresenceService.recordHeartbeat(memberId, now + 2000);

    long onlineMemberCount = onlinePresenceService.countOnlineMembers(now + 2000);
    superLog(onlineMemberCount);
    assertEquals(1L, onlineMemberCount, "동일 회원의 반복 heartbeat는 1명으로 집계되어야 한다");
  }

  void 윈도우초과_회원은_조회시_청소되어_제외_테스트() {
    clearPresenceKey();
    long now = System.currentTimeMillis();

    UUID staleMemberId = UUID.randomUUID();   // 윈도우보다 오래 전 활동 (만료 대상)
    UUID activeMemberId = UUID.randomUUID();  // 방금 활동 (온라인)

    onlinePresenceService.recordHeartbeat(staleMemberId, now - ONLINE_WINDOW_MILLIS - 1000);
    onlinePresenceService.recordHeartbeat(activeMemberId, now);

    long onlineMemberCount = onlinePresenceService.countOnlineMembers(now);
    superLog(onlineMemberCount);
    assertEquals(1L, onlineMemberCount, "윈도우 초과 회원은 청소되어 활성 회원 1명만 남아야 한다");

    // 청소가 실제로 일어나 stale 멤버가 Set에서 제거됐는지 직접 확인
    Long remainingCount = redisTemplate.opsForZSet().zCard(ONLINE_PRESENCE_KEY);
    assertTrue(remainingCount != null && remainingCount == 1L,
        "조회 시 ZREMRANGEBYSCORE로 stale 멤버가 물리적으로 청소되어야 한다");

    clearPresenceKey();
  }
}
