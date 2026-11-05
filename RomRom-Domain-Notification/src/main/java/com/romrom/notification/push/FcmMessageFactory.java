package com.romrom.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 푸시 내용 + 토큰 > FCM 메시지 (Android 채널·우선순위·보존 시간, iOS 소리·우선순위·만료·배지, 이미지 선택)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FcmMessageFactory {

  // 앱 AndroidManifest 기본 채널 id 와 같은 값
  public static final String ANDROID_CHANNEL_ID = "high_importance_channel";
  // APNs 즉시 전달 우선순위
  private static final String APNS_PRIORITY_IMMEDIATE = "10";
  private static final String APNS_PRIORITY_HEADER = "apns-priority";
  private static final String APNS_EXPIRATION_HEADER = "apns-expiration";
  private static final String APNS_DEFAULT_SOUND = "default";

  public static Message createMessage(FcmPushContent content, String token, Instant now) {
    boolean hasImage = StringUtils.hasText(content.imageUrl());

    Notification.Builder notificationBuilder = Notification.builder()
        .setTitle(content.title())
        .setBody(content.body());
    if (hasImage) {
      notificationBuilder.setImage(content.imageUrl());
    }

    // Android: 앱 채널, 높은 우선순위, 보존 시간
    AndroidConfig androidConfig = AndroidConfig.builder()
        .setPriority(AndroidConfig.Priority.HIGH)
        .setTtl(content.timeToLive().toMillis())
        .setNotification(AndroidNotification.builder()
            .setChannelId(ANDROID_CHANNEL_ID)
            .setDefaultSound(true)
            .build())
        .build();

    // iOS: 기본 소리, 즉시 전달, 만료 시각, 이미지 있으면 mutable-content, 배지(단건만)
    Aps.Builder apsBuilder = Aps.builder()
        .setSound(APNS_DEFAULT_SOUND)
        .setMutableContent(hasImage);
    if (content.badgeCount() != null) {
      apsBuilder.setBadge(content.badgeCount());
    }
    ApnsConfig apnsConfig = ApnsConfig.builder()
        .putHeader(APNS_PRIORITY_HEADER, APNS_PRIORITY_IMMEDIATE)
        .putHeader(APNS_EXPIRATION_HEADER, String.valueOf(now.plus(content.timeToLive()).getEpochSecond()))
        .setAps(apsBuilder.build())
        .build();

    Message.Builder messageBuilder = Message.builder()
        .setToken(token)
        .setNotification(notificationBuilder.build())
        .setAndroidConfig(androidConfig)
        .setApnsConfig(apnsConfig);
    if (content.data() != null) {
      messageBuilder.putAllData(content.data());
    }
    return messageBuilder.build();
  }
}
