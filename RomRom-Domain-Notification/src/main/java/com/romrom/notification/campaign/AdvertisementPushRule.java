package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 광고성 푸시 규칙 (정보통신망법): 제목 "(광고) " 표시, 본문 수신거부 안내, 21:00~08:00(KST) 발송 금지
 * 정보성은 제목·본문 그대로
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvertisementPushRule {

  public static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  public static final String ADVERTISEMENT_TITLE_PREFIX = "(광고) ";
  public static final String ADVERTISEMENT_BODY_SUFFIX = "\n수신거부: 설정 > 알림 설정";
  // 야간 금지 구간 (시작 포함·종료 제외)
  public static final LocalTime NIGHT_RESTRICTION_START_TIME = LocalTime.of(21, 0);
  public static final LocalTime NIGHT_RESTRICTION_END_TIME = LocalTime.of(8, 0);

  /**
   * 표시 제목: 광고성 > "(광고) " 접두 (이미 있으면 그대로)
   */
  public static String toDisplayTitle(PushCampaignPurpose purpose, String title) {
    if (purpose != PushCampaignPurpose.ADVERTISEMENT || title.startsWith(ADVERTISEMENT_TITLE_PREFIX)) {
      return title;
    }
    return ADVERTISEMENT_TITLE_PREFIX + title;
  }

  /**
   * 표시 본문: 광고성 > 수신거부 안내 접미 (이미 있으면 그대로)
   */
  public static String toDisplayBody(PushCampaignPurpose purpose, String body) {
    if (purpose != PushCampaignPurpose.ADVERTISEMENT || body.endsWith(ADVERTISEMENT_BODY_SUFFIX)) {
      return body;
    }
    return body + ADVERTISEMENT_BODY_SUFFIX;
  }

  /**
   * 야간 금지 시각 여부 (KST 시각, 21:00 포함 ~ 08:00 제외)
   */
  public static boolean isNightRestrictedAt(LocalTime kstTime) {
    return !kstTime.isBefore(NIGHT_RESTRICTION_START_TIME) || kstTime.isBefore(NIGHT_RESTRICTION_END_TIME);
  }

  /**
   * 광고성이면서 야간 금지 시각인지 (KST 일시)
   */
  public static boolean isRestrictedAdvertisementAt(PushCampaignPurpose purpose, LocalDateTime kstDateTime) {
    return purpose == PushCampaignPurpose.ADVERTISEMENT && isNightRestrictedAt(kstDateTime.toLocalTime());
  }

  /**
   * 현재 KST 일시
   */
  public static LocalDateTime nowInKorea() {
    return LocalDateTime.now(KOREA_ZONE);
  }
}
