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

  // 새 알림 타입 표시 가능 앱 여부 (#861): true 아니면 목록·미확인 개수에서 확장 타입 제외
  @Schema(description = "확장 알림 타입(수락·거절·완료·후기) 표시 가능 여부 (새 앱만 true)", defaultValue = "false")
  private Boolean isExtendedTypeSupported;
}
