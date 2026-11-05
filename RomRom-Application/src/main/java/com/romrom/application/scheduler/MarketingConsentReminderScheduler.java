package com.romrom.application.scheduler;

import com.romrom.notification.campaign.MarketingConsentReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마케팅 수신 동의 2년 안내 (#874): 매일 11:00 KST (낮 시간), 회원 선점 후 발송이라 다중 인스턴스에도 1회
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketingConsentReminderScheduler {

  private final MarketingConsentReminderService marketingConsentReminderService;

  @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Seoul")
  public void sendDueReminders() {
    try {
      int remindedMemberCount = marketingConsentReminderService.sendDueReminders();
      if (remindedMemberCount > 0) {
        log.info("[marketing consent reminder] sent : members={}", remindedMemberCount);
      }
    } catch (RuntimeException schedulerException) {
      log.error("[marketing consent reminder] scheduler failed : {}", schedulerException.getMessage(), schedulerException);
    }
  }
}
