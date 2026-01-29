package com.romrom.notification.event;

import com.romrom.common.util.CommonUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class NotificationEvent {

  private final UUID targetMemberId;
  private final NotificationType notificationType;
  private final LocalDateTime publishedAt;
  private final Map<String, String> payload;

  protected NotificationEvent(UUID targetMemberId, NotificationType notificationType) {
    this.targetMemberId = targetMemberId;
    this.notificationType = notificationType;
    this.publishedAt = LocalDateTime.now();
    this.payload = new HashMap<>();

    this.payload.put("notificationType", notificationType.name());
    this.payload.put("publishedAt", String.valueOf(publishedAt));
  }

  public abstract String getTitle();

  public abstract String getBody();

  protected void addPayload(String key, String value) {
    if (!CommonUtil.nvl(value, "").isEmpty()) {
      this.payload.put(key, value);
    }
  }

  protected void addPayload(String key, UUID value) {
    if (value != null) {
      this.payload.put(key, value.toString());
    }
  }

  protected void setDeepLink(String deepLink) {
    this.payload.put("deepLink", deepLink);
  }
}
