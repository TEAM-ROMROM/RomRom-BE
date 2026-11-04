package com.romrom.application.scheduler;

import com.romrom.chat.service.ChatAppointmentReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 확정 약속 리마인드 스케줄러 (#860): 1분 간격, 약속 1시간 전 진입 건 발송
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatAppointmentReminderScheduler {

  // 실행 간격 (1분, 이전 실행 종료 기준)
  private static final long REMINDER_CHECK_INTERVAL_MILLISECONDS = 60_000L;

  private final ChatAppointmentReminderService chatAppointmentReminderService;

  @Scheduled(fixedDelay = REMINDER_CHECK_INTERVAL_MILLISECONDS, initialDelay = REMINDER_CHECK_INTERVAL_MILLISECONDS)
  public void sendDueAppointmentReminders() {
    try {
      int remindedAppointmentCount = chatAppointmentReminderService.sendDueReminders();
      if (remindedAppointmentCount > 0) {
        log.info("[appointment reminder] sent : appointments={}", remindedAppointmentCount);
      }
    } catch (Exception schedulerException) {
      log.error("[appointment reminder] scheduler failed : {}", schedulerException.getMessage(), schedulerException);
    }
  }
}
