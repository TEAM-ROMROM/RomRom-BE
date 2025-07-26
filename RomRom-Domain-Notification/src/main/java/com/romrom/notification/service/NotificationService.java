package com.romrom.notification.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.ApnsFcmOptions;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.romrom.common.constant.NotificationConstants;
import com.romrom.notification.dto.NotificationRequest;
import com.romrom.notification.entity.FcmToken;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final FcmTokenService fcmTokenService;

  /**
   * 사용자 리스트에게 알림 전송 (단일 or 다수)
   */
  @Transactional
  public void sendToMembers(NotificationRequest request) {
    for (UUID memberId : request.getMemberIdList()) {
      List<FcmToken> tokens = fcmTokenService.findAllTokensByMemberId(memberId);

      if (tokens.isEmpty()) {
        log.warn("회원 {} 에게 전송할 FCM 토큰이 없습니다.", memberId);
        continue;
      }

      for (FcmToken token : tokens) {
        sendToToken(token, request.getTitle(), request.getBody());
      }
    }
  }

  /**
   * 전체 사용자에게 알림 전송
   */
  @Transactional
  public void sendToAll(NotificationRequest request) {
    List<FcmToken> tokens = fcmTokenService.findAllTokens();

    if (tokens.isEmpty()) {
      log.debug("전체 사용자에게 보낼 FCM 토큰이 없습니다.");
      return;
    }

    for (FcmToken token : tokens) {
      sendToToken(token, request.getTitle(), request.getBody());
    }
  }

  /**
   * FCM 토큰 1개에 푸시 전송
   */
  private void sendToToken(FcmToken token, String title, String body) {
    try {
      Notification notification = Notification.builder()
          .setTitle(title)
          .setBody(body)
          .build();

      // Android 세부 설정
      AndroidNotification androidNotification = AndroidNotification.builder()
          .setImage(NotificationConstants.NOTIFICATION_ICON_PATH)
          .build();

      AndroidConfig androidConfig = AndroidConfig.builder()
          .setNotification(androidNotification)
          .build();

      // iOS 세부 설정
      ApnsFcmOptions apnsFcmOptions = ApnsFcmOptions.builder()
          .setImage(NotificationConstants.NOTIFICATION_ICON_PATH)
          .build();

      Aps aps = Aps.builder()
          .setMutableContent(true)
          .build();

      ApnsConfig apnsConfig = ApnsConfig.builder()
          .setAps(aps)
          .setFcmOptions(apnsFcmOptions)
          .build();

      Message message = Message.builder()
          .setToken(token.getToken())
          .setNotification(notification)
          .setAndroidConfig(androidConfig)
          .setApnsConfig(apnsConfig)
          .build();

      String response = FirebaseMessaging.getInstance().send(message);
      log.debug("푸시 전송 성공 (member: {}, device: {}, 응답: {})",
          token.getMemberId(), token.getDeviceType(), response);

    } catch (Exception e) {
      log.error("푸시 전송 실패 (member: {}, device: {}, token: {})",
          token.getMemberId(), token.getDeviceType(), token.getToken(), e);
    }
  }
}
