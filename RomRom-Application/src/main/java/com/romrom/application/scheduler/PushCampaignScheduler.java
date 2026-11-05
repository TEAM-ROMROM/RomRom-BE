package com.romrom.application.scheduler;

import com.romrom.notification.campaign.PushCampaignDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약 푸시 스케줄러 (#874): 1분 간격, 중단된 발송 정리 > 시각 도래 캠페인 선점·실행
 * 선점이 조건부 UPDATE 라 블루그린 두 인스턴스가 동시에 돌아도 캠페인당 1회 발송
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PushCampaignScheduler {

  // 실행 간격 (1분, 이전 실행 종료 기준)
  private static final long CAMPAIGN_CHECK_INTERVAL_MILLISECONDS = 60_000L;

  private final PushCampaignDispatcher pushCampaignDispatcher;

  @Scheduled(fixedDelay = CAMPAIGN_CHECK_INTERVAL_MILLISECONDS, initialDelay = CAMPAIGN_CHECK_INTERVAL_MILLISECONDS)
  public void dispatchDueCampaigns() {
    try {
      pushCampaignDispatcher.failStaleSendingCampaigns();
      int dispatchedCampaignCount = pushCampaignDispatcher.dispatchDueCampaigns();
      if (dispatchedCampaignCount > 0) {
        log.info("[push campaign] scheduled dispatch : campaigns={}", dispatchedCampaignCount);
      }
    } catch (RuntimeException schedulerException) {
      log.error("[push campaign] scheduler failed : {}", schedulerException.getMessage(), schedulerException);
    }
  }
}
