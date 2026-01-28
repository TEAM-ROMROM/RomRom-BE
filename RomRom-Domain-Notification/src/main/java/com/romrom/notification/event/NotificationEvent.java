package com.romrom.notification.event;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class NotificationEvent {

  private final UUID targetMemberId;
  private final NotificationType notificationType;
  private final LocalDateTime publishedAt;
  private String deepLink;

  protected NotificationEvent(UUID targetMemberId, NotificationType notificationType) {
    this.targetMemberId = targetMemberId;
    this.notificationType = notificationType;
    this.publishedAt = LocalDateTime.now();
  }

  public abstract String getTitle();

  public abstract String getBody();

  protected void setDeepLink(String deepLink) {
    this.deepLink = deepLink;
  }
}
