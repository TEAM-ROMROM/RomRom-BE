package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 확정 약속 리마인드 알림 (약속 1시간 전, 방별 알림 끄기 무시)
 * 옛 앱 호환: 새 NotificationType 대신 CHAT_MESSAGE_RECEIVED 재사용 + chatPushKind 로 구분
 * - Title: 약속 알림
 * - Body: {일시} {장소} 약속이 곧 다가와요
 */
@Getter
public class ChatAppointmentReminderEvent extends NotificationEvent {

  // payload chatPushKind 값: 약속 리마인드
  public static final String CHAT_PUSH_KIND_APPOINTMENT_REMINDER = "APPOINTMENT_REMINDER";
  private static final String REMINDER_TITLE = "약속 알림";
  private static final String REMINDER_BODY_FORMAT = "%s %s 약속이 곧 다가와요";

  private final UUID chatRoomId;
  private final String chatMessageId;
  private final String appointmentTimeLabel;
  private final String placeName;

  public ChatAppointmentReminderEvent(
      UUID targetMemberId,
      UUID chatRoomId,
      String chatMessageId,
      String appointmentTimeLabel,
      String placeName
  ) {
    super(targetMemberId, NotificationType.CHAT_MESSAGE_RECEIVED);
    this.chatRoomId = chatRoomId;
    this.chatMessageId = chatMessageId;
    this.appointmentTimeLabel = appointmentTimeLabel;
    this.placeName = placeName;
    addPayload("chatRoomId", chatRoomId);
    addPayload("chatMessageId", chatMessageId);
    addPayload("chatPushKind", CHAT_PUSH_KIND_APPOINTMENT_REMINDER);
    setDeepLink("romrom://chat/room?chatRoomId=" + chatRoomId);
  }

  @Override
  public String getTitle() {
    return REMINDER_TITLE;
  }

  @Override
  public String getBody() {
    return String.format(REMINDER_BODY_FORMAT, appointmentTimeLabel, placeName);
  }
}
