package com.romrom.application.scheduler;

import com.romrom.common.constant.ServiceTimeZone;
import com.romrom.notification.service.FcmTokenService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 FCM 토큰 정리 (#874): 매일 04:30 KST, 갱신 후 90일 지난 토큰 삭제 (멱등 DELETE)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmTokenCleanupScheduler {

  private final FcmTokenService fcmTokenService;

  @Scheduled(cron = "0 30 4 * * *", zone = ServiceTimeZone.KST_ZONE_ID_NAME)
  public void deleteStaleTokens() {
    try {
      fcmTokenService.deleteStaleTokens(LocalDateTime.now());
    } catch (RuntimeException cleanupException) {
      log.warn("[fcm token cleanup] failed : {}", cleanupException.getMessage());
    }
  }
}
