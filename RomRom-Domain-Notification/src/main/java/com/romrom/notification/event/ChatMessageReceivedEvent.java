package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 채팅 메시지 수신 알림
 * - Title: {메시지 내용}
 * - Body: {메시지 내용}
 */
@Getter
public class ChatMessageReceivedEvent extends NotificationEvent {

  private final UUID chatRoomId;
  private final String content;

  public ChatMessageReceivedEvent(
    UUID targetMemberId,
    UUID chatRoomId,
    String content
  ) {
    super(targetMemberId, NotificationType.CHAT_MESSAGE_RECEIVED);
    this.chatRoomId = chatRoomId;
    this.content = content;
    addPayload("chatRoomId", chatRoomId);
    setDeepLink("romrom://chat/room?chatRoomId=" + chatRoomId);
  }

  @Override
  public String getTitle() {
    return String.format(getNotificationType().getTitle(), content);
  }

  @Override
  public String getBody() {
    return String.format(getNotificationType().getBody(), content);
  }
}
