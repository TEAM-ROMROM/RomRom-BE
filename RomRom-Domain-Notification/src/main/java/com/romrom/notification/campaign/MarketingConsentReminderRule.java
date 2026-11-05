package com.romrom.notification.campaign;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 마케팅 수신 동의 2년 안내 규칙 (정보통신망법 시행령 62조의3)
 * 기준 시각 = max(동의 변경 시각, 마지막 안내 시각), 동의 중 + 기준 시각 + 2년 <= 현재 > 안내 대상
 * MarketingConsentReminderService 의 SQL(GREATEST)과 같은 기준
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarketingConsentReminderRule {

  public static final Period REMINDER_INTERVAL = Period.ofYears(2);
  public static final String REMINDER_TITLE = "마케팅 정보 수신 동의 상태 안내";
  private static final DateTimeFormatter CONSENT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  /**
   * 안내 대상 여부 (동의 아님·기준 시각 없음 > false)
   */
  public static boolean isReminderDue(Boolean isMarketingAgreed, LocalDateTime consentChangedAt,
      LocalDateTime consentRemindedAt, LocalDateTime now) {
    if (!Boolean.TRUE.equals(isMarketingAgreed)) {
      return false;
    }
    LocalDateTime reminderBaseAt = toReminderBaseAt(consentChangedAt, consentRemindedAt);
    return reminderBaseAt != null && !reminderBaseAt.plus(REMINDER_INTERVAL).isAfter(now);
  }

  /**
   * 안내 기준 시각 = 둘 중 늦은 값 (null 무시)
   */
  public static LocalDateTime toReminderBaseAt(LocalDateTime consentChangedAt, LocalDateTime consentRemindedAt) {
    if (consentChangedAt == null) {
      return consentRemindedAt;
    }
    if (consentRemindedAt == null) {
      return consentChangedAt;
    }
    return consentChangedAt.isAfter(consentRemindedAt) ? consentChangedAt : consentRemindedAt;
  }

  /**
   * 안내 본문: "마케팅 정보 수신 동의 상태 안내 (동의일 yyyy.MM.dd)" + 변경 방법
   */
  public static String toReminderBody(LocalDateTime consentChangedAt) {
    String consentDateText = consentChangedAt == null ? "-" : consentChangedAt.format(CONSENT_DATE_FORMAT);
    return REMINDER_TITLE + " (동의일 " + consentDateText + ")\n수신 동의 중이에요. 설정 > 알림 설정에서 바꿀 수 있어요.";
  }
}
