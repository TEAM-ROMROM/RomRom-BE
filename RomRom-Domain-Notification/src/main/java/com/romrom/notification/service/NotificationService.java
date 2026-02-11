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
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberService;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.entity.FcmToken;
import com.romrom.notification.event.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final FcmTokenService fcmTokenService;
  private final NotificationHistoryService notificationHistoryService;
  private final MemberService memberService;

  /**
   * 단일 사용자 알림 전송
   */
  public void sendToMember(UUID memberId, String title, String body, Map<String, String> payload) {
    // 알림 히스토리 저장
    NotificationType notificationType = NotificationType.valueOf(payload.get("notificationType"));
    LocalDateTime publishedAt = LocalDateTime.parse(payload.get("publishedAt"));
    Member member = memberService.findMemberById(memberId);

    try {
      notificationHistoryService.saveNotificationHistory(
        NotificationHistoryRequest.builder()
          .member(member)
          .notificationType(notificationType)
          .title(title)
          .body(body)
          .payload(payload)
          .publishedAt(publishedAt)
          .build()
      );
    } catch (Exception e) {
      log.error("알림 히스토리 저장 실패: memberId={}, title={}", memberId, title, e);
      // 히스토리 저장 실패 시에도 FCM 알림 발송 진행
    }

    // 푸시 알림 설정 체크: 해당 카테고리의 알림이 비활성화 상태면 FCM 발송 스킵
    if (isNotificationTypeAgreed(member, notificationType)) {
      log.debug("푸시 알림 비활성화 상태로 FCM 발송 스킵: memberId={}, type={}",
        memberId, notificationType);
      return;
    }

    // FCM 알림 전송
    List<FcmToken> tokens = fcmTokenService.findAllTokensByMemberId(memberId);
    tokens.forEach(token -> send(token, title, body, payload));
  }

  /**
   * 사용자 리스트에게 알림 전송 (단일 or 다수)
   */
  public void sendToMembers(List<UUID> memberIds, String title, String body, Map<String, String> payload) { // TODO: 추후 알림 도메인 구성 후 파라미터 수정
    memberIds.forEach(memberId -> sendToMember(memberId, title, body, payload));
  }

  /**
   * 전체 사용자에게 알림 전송
   */
  public void sendToAll(String title, String body, Map<String, String> payload) { // TODO: 추후 알림 도메인 구성 후 파라미터 수정
    List<FcmToken> tokens = fcmTokenService.findAllTokens();

    if (tokens.isEmpty()) {
      log.debug("전체 사용자에게 보낼 FCM 토큰이 없습니다.");
      return;
    }

    for (FcmToken token : tokens) {
      send(token, title, body, payload);
    }
  }

  /**
   * FCM 토큰 1개에 푸시 전송
   */
  private void send(FcmToken token, String title, String body, Map<String, String> payload) {
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
          .putAllData(payload)
          .build();

      String response = FirebaseMessaging.getInstance().send(message);
      log.debug("푸시 전송 성공 (member: {}, device: {}, 응답: {})",
          token.getMember().getMemberId(), token.getDeviceType(), response);

    } catch (Exception e) {
      log.error("푸시 전송 실패 (member: {}, device: {}, token: {})",
          token.getMember().getMemberId(), token.getDeviceType(), token.getToken(), e);
    }
  }

  /**
   * NotificationType 별 알림 동의 여부 확인
   */
  private boolean isNotificationTypeAgreed(Member member, NotificationType notificationType) {
    switch (notificationType) {
      case ITEM_LIKED -> {
        return Boolean.TRUE.equals(member.getIsActivityNotificationAgreed());
      }
      case TRADE_REQUEST_RECEIVED -> {
        return Boolean.TRUE.equals(member.getIsTradeNotificationAgreed());
      }
      case CHAT_MESSAGE_RECEIVED -> {
        return Boolean.TRUE.equals(member.getIsChatNotificationAgreed());
      }
      case SYSTEM_NOTICE -> {
        return Boolean.TRUE.equals(member.getIsContentNotificationAgreed());
      }
    }
    return true;
  }
}
