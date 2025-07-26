package com.romrom.notification.entity;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.NotificationConstants;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@RedisHash(value = "fcmToken", timeToLive = NotificationConstants.FCM_TOKEN_TTL)
@Setter
public class FcmToken {

  @Id
  private String fcmTokenId;

  private String token;

  @Indexed
  private UUID memberId;

  private DeviceType deviceType;

  @Builder
  public FcmToken(String token, UUID memberId, DeviceType deviceType) {
    this.fcmTokenId = memberId + "-" + deviceType;
    this.token = token;
    this.memberId = memberId;
    this.deviceType = deviceType;
  }
}