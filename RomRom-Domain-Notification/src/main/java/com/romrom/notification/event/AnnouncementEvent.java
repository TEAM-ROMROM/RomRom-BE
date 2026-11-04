package com.romrom.notification.event;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public class AnnouncementEvent {

  private static final String ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX = "romrom://notice/detail?announcementId=";

  private final UUID announcementId;
  private final String title;
  private final String body;
  private final Map<String, String> payload;

  public AnnouncementEvent(UUID announcementId, String announcementTitle) {
    this.announcementId = announcementId;
    this.title = NotificationType.ANNOUNCEMENT.getTitle();
    this.body = String.format(NotificationType.ANNOUNCEMENT.getBody(), announcementTitle);

    this.payload = new HashMap<>();
    this.payload.put("notificationType", NotificationType.ANNOUNCEMENT.name());
    this.payload.put("publishedAt", LocalDateTime.now().toString());
    this.payload.put("announcementId", announcementId.toString());
    // 공지 상세 화면 (#861, 옛 앱은 모르는 경로 > 알림함으로 폴백)
    this.payload.put("deepLink", ANNOUNCEMENT_DETAIL_DEEP_LINK_PREFIX + announcementId);
  }
}
