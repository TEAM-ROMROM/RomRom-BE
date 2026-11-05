package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.event.NotificationType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 캠페인 > 푸시 data·알림함 payload (notificationType, publishedAt, campaignId, deepLink, 공지·물품 ID)
 * 공지·물품 ID: 딥링크를 모르는 옛 앱이 같은 화면으로 이동하도록 함께 전달
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PushCampaignPayloadFactory {

  // 앱 딥링크 경로 (앱 deep_link_router 와 함께 변경)
  public static final String ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX = "romrom://notice/detail?announcementId=";
  public static final String ITEM_DETAIL_DEEP_LINK_PREFIX = "romrom://item/detail?itemId=";
  public static final String CAMPAIGN_ID_PAYLOAD_KEY = "campaignId";

  /**
   * 목적 > 알림 종류 (정보성 ANNOUNCEMENT, 광고성 MARKETING)
   */
  public static NotificationType toNotificationType(PushCampaignPurpose purpose) {
    return purpose == PushCampaignPurpose.ADVERTISEMENT ? NotificationType.MARKETING : NotificationType.ANNOUNCEMENT;
  }

  public static Map<String, String> createPayload(PushCampaign pushCampaign, LocalDateTime publishedAt) {
    Map<String, String> payload = new HashMap<>();
    payload.put("notificationType", toNotificationType(pushCampaign.getPurpose()).name());
    payload.put("publishedAt", publishedAt.toString());
    payload.put(CAMPAIGN_ID_PAYLOAD_KEY, pushCampaign.getPushCampaignId().toString());
    String deepLink = pushCampaign.getDeepLink();
    if (StringUtils.hasText(deepLink)) {
      payload.put("deepLink", deepLink);
      putIdIfPresent(payload, "announcementId", deepLink, ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX);
      putIdIfPresent(payload, "itemId", deepLink, ITEM_DETAIL_DEEP_LINK_PREFIX);
    }
    return payload;
  }

  // 딥링크 접두 일치 + 뒤가 UUID > ID 키 추가
  private static void putIdIfPresent(Map<String, String> payload, String payloadKey, String deepLink, String deepLinkPrefix) {
    if (!deepLink.startsWith(deepLinkPrefix)) {
      return;
    }
    String idText = deepLink.substring(deepLinkPrefix.length());
    try {
      payload.put(payloadKey, UUID.fromString(idText).toString());
    } catch (IllegalArgumentException invalidIdException) {
      // ID 형식 아님 > 딥링크만 전달
    }
  }
}
