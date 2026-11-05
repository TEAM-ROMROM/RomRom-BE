package com.romrom.notification.push;

import java.time.Duration;
import java.util.Map;

/**
 * 푸시 내용: 제목, 본문, 이미지(선택), data, 보존 시간, iOS 배지(선택, 단건 발송만)
 */
public record FcmPushContent(
    String title,
    String body,
    String imageUrl,
    Map<String, String> data,
    Duration timeToLive,
    Integer badgeCount
) {

  // 기본 보존 시간 (정보성·이벤트 알림, 1일)
  public static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofDays(1);
  // 광고성 보존 시간 (6시간: 늦게 도착해 야간에 표시되는 것 방지)
  public static final Duration ADVERTISEMENT_TIME_TO_LIVE = Duration.ofHours(6);
}
