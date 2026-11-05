package com.romrom.notification.campaign;

import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마케팅 수신 동의 기록·2년 안내 단위 테스트 (#874): 변경 시각 기록, 안내 대상 선정, 안내 후 다음 주기, 본문
 */
class MarketingConsentReminderRuleTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 11, 0);

  @Test
  @DisplayName("동의 값이 바뀔 때만 변경 시각 기록·안내 시각 초기화, 같은 값·null 은 유지")
  void changeMarketingConsent_변경시각() {
    LocalDateTime firstChangedAt = NOW.minusYears(1);
    Member member = Member.builder().isMarketingInfoAgreed(false).build();

    member.changeMarketingConsent(true, firstChangedAt);
    Assertions.assertTrue(member.getIsMarketingInfoAgreed());
    Assertions.assertEquals(firstChangedAt, member.getMarketingConsentChangedAt());

    member.setMarketingConsentRemindedAt(NOW.minusDays(1));
    member.changeMarketingConsent(true, NOW);
    member.changeMarketingConsent(null, NOW);
    Assertions.assertEquals(firstChangedAt, member.getMarketingConsentChangedAt());
    Assertions.assertNotNull(member.getMarketingConsentRemindedAt());

    member.changeMarketingConsent(false, NOW);
    Assertions.assertFalse(member.getIsMarketingInfoAgreed());
    Assertions.assertEquals(NOW, member.getMarketingConsentChangedAt());
    Assertions.assertNull(member.getMarketingConsentRemindedAt());
  }

  @Test
  @DisplayName("같은 값이어도 변경 시각이 없으면 첫 기록 (약관 동의 시점)")
  void changeMarketingConsent_첫기록() {
    Member member = Member.builder().isMarketingInfoAgreed(false).build();
    member.changeMarketingConsent(false, NOW);
    Assertions.assertEquals(NOW, member.getMarketingConsentChangedAt());
  }

  @Test
  @DisplayName("동의 후 2년 경계: 2년 전 정각 대상, 1초 모자라면 아님, 비동의·기준 없음 제외")
  void isReminderDue_2년경계() {
    Assertions.assertTrue(MarketingConsentReminderRule.isReminderDue(true, NOW.minusYears(2), null, NOW));
    Assertions.assertFalse(MarketingConsentReminderRule.isReminderDue(true, NOW.minusYears(2).plusSeconds(1), null, NOW));
    Assertions.assertFalse(MarketingConsentReminderRule.isReminderDue(false, NOW.minusYears(3), null, NOW));
    Assertions.assertFalse(MarketingConsentReminderRule.isReminderDue(true, null, null, NOW));
  }

  @Test
  @DisplayName("안내 후 다음 안내는 안내 시각 + 2년 (늦은 기준 시각 사용)")
  void isReminderDue_안내후다음주기() {
    LocalDateTime consentChangedAt = NOW.minusYears(3);
    Assertions.assertFalse(MarketingConsentReminderRule.isReminderDue(true, consentChangedAt, NOW.minusYears(1), NOW));
    Assertions.assertTrue(MarketingConsentReminderRule.isReminderDue(true, consentChangedAt, NOW.minusYears(2), NOW));
    Assertions.assertEquals(NOW.minusYears(1),
        MarketingConsentReminderRule.toReminderBaseAt(NOW.minusYears(3), NOW.minusYears(1)));
  }

  @Test
  @DisplayName("안내 본문에 동의일 yyyy.MM.dd")
  void toReminderBody_동의일() {
    String reminderBody = MarketingConsentReminderRule.toReminderBody(LocalDateTime.of(2024, 3, 7, 10, 0));
    Assertions.assertTrue(reminderBody.startsWith("마케팅 정보 수신 동의 상태 안내 (동의일 2024.03.07)"));
  }
}
