package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 광고성 푸시 규칙 단위 테스트 (#874): "(광고) " 접두, 수신거부 접미, 야간 경계 21:00·07:59·08:00
 */
class AdvertisementPushRuleTest {

  @Test
  @DisplayName("광고성 제목 앞 '(광고) ', 본문 끝 수신거부 안내 (중복 부착 없음)")
  void 광고성_표시문구() {
    String displayTitle = AdvertisementPushRule.toDisplayTitle(PushCampaignPurpose.ADVERTISEMENT, "주말 이벤트");
    String displayBody = AdvertisementPushRule.toDisplayBody(PushCampaignPurpose.ADVERTISEMENT, "참여하세요");

    Assertions.assertEquals("(광고) 주말 이벤트", displayTitle);
    Assertions.assertEquals("참여하세요\n수신거부: 설정 > 알림 설정", displayBody);
    Assertions.assertEquals(displayTitle, AdvertisementPushRule.toDisplayTitle(PushCampaignPurpose.ADVERTISEMENT, displayTitle));
    Assertions.assertEquals(displayBody, AdvertisementPushRule.toDisplayBody(PushCampaignPurpose.ADVERTISEMENT, displayBody));
  }

  @Test
  @DisplayName("정보성은 제목·본문 그대로")
  void 정보성_그대로() {
    Assertions.assertEquals("공지", AdvertisementPushRule.toDisplayTitle(PushCampaignPurpose.INFORMATION, "공지"));
    Assertions.assertEquals("본문", AdvertisementPushRule.toDisplayBody(PushCampaignPurpose.INFORMATION, "본문"));
  }

  @Test
  @DisplayName("야간 경계: 20:59 허용, 21:00 금지, 07:59 금지, 08:00 허용")
  void 야간경계() {
    Assertions.assertFalse(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(20, 59)));
    Assertions.assertTrue(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(21, 0)));
    Assertions.assertTrue(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(0, 0)));
    Assertions.assertTrue(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(7, 59)));
    Assertions.assertFalse(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(8, 0)));
    Assertions.assertFalse(AdvertisementPushRule.isNightRestrictedAt(LocalTime.of(12, 0)));
  }

  @Test
  @DisplayName("야간 제한은 광고성에만 적용")
  void 야간제한_광고성만() {
    LocalDateTime nightTime = LocalDateTime.of(2026, 11, 5, 22, 0);
    Assertions.assertTrue(AdvertisementPushRule.isRestrictedAdvertisementAt(PushCampaignPurpose.ADVERTISEMENT, nightTime));
    Assertions.assertFalse(AdvertisementPushRule.isRestrictedAdvertisementAt(PushCampaignPurpose.INFORMATION, nightTime));
  }
}
