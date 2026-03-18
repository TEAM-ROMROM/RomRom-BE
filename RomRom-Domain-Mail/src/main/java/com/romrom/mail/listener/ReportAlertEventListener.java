package com.romrom.mail.listener;

import com.romrom.common.service.SystemConfigCacheService;
import com.romrom.mail.service.MailService;
import com.romrom.report.enums.ReportType;
import com.romrom.report.event.ReportAlertEvent;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReportAlertEventListener {

  private static final String THROTTLE_KEY_PREFIX = "REPORT:ALERT:";
  private static final String CONFIG_KEY_ALERT_EMAIL = "admin.alert.email";
  private static final String CONFIG_KEY_THROTTLE_MINUTES = "admin.alert.throttle.minutes";
  private static final int DEFAULT_THROTTLE_MINUTES = 30;
  private static final String ADMIN_PAGE_BASE_URL = "https://api.romrom.xyz/admin";
  private static final DateTimeFormatter REPORT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final MailService mailService;
  private final SystemConfigCacheService systemConfigCacheService;
  private final RedisTemplate<String, Object> redisTemplate;
  private final TemplateEngine templateEngine;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleReportAlert(ReportAlertEvent reportAlertEvent) {
    try {
      String adminAlertEmail = systemConfigCacheService.get(CONFIG_KEY_ALERT_EMAIL);
      if (adminAlertEmail == null || adminAlertEmail.isBlank()) {
        log.warn("관리자 알림 이메일이 설정되지 않았습니다. 알림을 건너뜁니다.");
        return;
      }

      if (!mailService.isConfigured()) {
        log.warn("메일 서비스가 초기화되지 않았습니다. 알림을 건너뜁니다.");
        return;
      }

      String throttleRedisKey = THROTTLE_KEY_PREFIX + reportAlertEvent.getReportType() + ":" + reportAlertEvent.getTargetId();
      int throttleMinutes = parseIntOrDefault(
          systemConfigCacheService.getOrDefault(CONFIG_KEY_THROTTLE_MINUTES, String.valueOf(DEFAULT_THROTTLE_MINUTES)),
          DEFAULT_THROTTLE_MINUTES
      );

      Boolean throttleKeyAcquired = redisTemplate.opsForValue()
          .setIfAbsent(throttleRedisKey, "1", throttleMinutes, TimeUnit.MINUTES);
      if (Boolean.FALSE.equals(throttleKeyAcquired)) {
        log.debug("쓰로틀링 적용 - 알림 스킵: key={}", throttleRedisKey);
        return;
      }

      String reportTypeDisplayName = getReportTypeDisplayName(reportAlertEvent.getReportType());
      String emailSubject = String.format("[RomRom] 새 신고 접수 - %s", reportTypeDisplayName);

      Context thymeleafContext = new Context();
      thymeleafContext.setVariable("reportType", reportTypeDisplayName);
      thymeleafContext.setVariable("targetName", reportAlertEvent.getTargetName());
      thymeleafContext.setVariable("reportReasons", reportAlertEvent.getReportReasons());
      thymeleafContext.setVariable("extraComment", reportAlertEvent.getExtraComment());
      thymeleafContext.setVariable("reportedAt", reportAlertEvent.getReportedAt().format(REPORT_DATETIME_FORMATTER));
      thymeleafContext.setVariable("adminPageUrl", ADMIN_PAGE_BASE_URL + "/reports");

      String emailHtmlContent = templateEngine.process("mail/report-alert", thymeleafContext);
      mailService.sendHtmlEmail(adminAlertEmail, emailSubject, emailHtmlContent);

      log.info("신고 알림 이메일 발송 완료: type={}, targetId={}, throttle={}분",
          reportAlertEvent.getReportType(), reportAlertEvent.getTargetId(), throttleMinutes);

    } catch (Exception reportAlertException) {
      log.error("신고 알림 이메일 발송 실패: type={}, targetId={}, error={}",
          reportAlertEvent.getReportType(), reportAlertEvent.getTargetId(),
          reportAlertException.getMessage(), reportAlertException);
    }
  }

  private String getReportTypeDisplayName(ReportType reportType) {
    return switch (reportType) {
      case ITEM -> "물품신고";
      case MEMBER -> "회원신고";
    };
  }

  private int parseIntOrDefault(String value, int defaultValue) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException numberFormatException) {
      log.warn("숫자 파싱 실패, 기본값 사용: value={}, default={}", value, defaultValue);
      return defaultValue;
    }
  }
}
