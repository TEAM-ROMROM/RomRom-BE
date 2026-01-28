package com.romrom.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.romrom.member.entity.Member;
import com.romrom.notification.event.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationHistoryRequest {

  public NotificationHistoryRequest() {
    this.pageNumber = 0;
    this.pageSize = 30;
  }

  @Schema(hidden = true, description = "회원")
  @JsonIgnore
  private Member member;

  private UUID notificationHistoryId;

  private NotificationType notificationType;

  private String title;

  private String body;

  private Map<String, String> payload;

  private LocalDateTime publishedAt;

  private int pageNumber;

  private int pageSize;
}
