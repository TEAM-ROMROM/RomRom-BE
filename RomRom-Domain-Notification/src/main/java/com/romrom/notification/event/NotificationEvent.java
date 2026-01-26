package com.romrom.notification.event;

import com.romrom.common.util.CommonUtil;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class NotificationEvent {

  private static final String LOGO_IMAGE_URL = "http://suh-project.synology.me/romrom/images/romrom-logo.png";

  private final UUID targetMemberId;
  private final NotificationType notificationType;
  private final LocalDateTime publishedAt;
  private final Map<String, String> data;
  private final String imageUrl;

  protected NotificationEvent(UUID targetMemberId, NotificationType notificationType) {
    this.targetMemberId = targetMemberId;
    this.notificationType = notificationType;
    this.publishedAt = LocalDateTime.now();
    this.data = new HashMap<>();
    this.imageUrl = LOGO_IMAGE_URL;
  }

  public abstract String getTitle();

  public abstract String getBody();

  protected void addData(String key, String value) {
    if (!CommonUtil.nvl(value, "").isEmpty()) {
      this.data.put(key, value);
    }
  }

  protected void addData(String key, UUID value) {
    if (value != null) {
      this.data.put(key, value.toString());
    }
  }
}
