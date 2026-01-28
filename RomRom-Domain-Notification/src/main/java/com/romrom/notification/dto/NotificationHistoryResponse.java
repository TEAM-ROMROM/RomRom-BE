package com.romrom.notification.dto;

import com.romrom.notification.entity.NotificationHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Page;

@ToString
@AllArgsConstructor
@Getter
@Setter
@Builder
public class NotificationHistoryResponse {

  private NotificationHistory notificationHistory;

  private Page<NotificationHistory> notificationHistoryPage;

  private Long unReadCount;
}
