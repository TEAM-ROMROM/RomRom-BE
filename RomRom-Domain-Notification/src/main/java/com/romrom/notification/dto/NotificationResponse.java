package com.romrom.notification.dto;

import com.romrom.common.constant.DeviceType;
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
public class NotificationResponse {

  private String tokenId;
  private String fcmToken;
  private UUID memberId;
  private DeviceType deviceType;
}