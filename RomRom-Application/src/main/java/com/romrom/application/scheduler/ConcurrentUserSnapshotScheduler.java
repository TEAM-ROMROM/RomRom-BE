package com.romrom.application.scheduler;

import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.service.ActivityEventRecorder;
import com.romrom.common.service.OnlinePresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 동시접속자 스냅샷 (#772): 5분마다 현재 온라인 수 > CONCURRENT_USERS 이벤트 (시간별 추이 원천)
 * 블루그린 전환 중 두 인스턴스가 함께 기록해도 시계열은 버킷 최대값이라 중복 영향 없음
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConcurrentUserSnapshotScheduler {

  private final OnlinePresenceService onlinePresenceService;
  private final ActivityEventRecorder activityEventRecorder;

  @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
  public void recordConcurrentUserSnapshot() {
    try {
      long onlineMemberCount = onlinePresenceService.countOnlineMembers(System.currentTimeMillis());
      activityEventRecorder.recordMetric(ActivityEventType.CONCURRENT_USERS, onlineMemberCount);
    } catch (RuntimeException snapshotException) {
      log.warn("[concurrent user snapshot] failed : {}", snapshotException.getMessage());
    }
  }
}
