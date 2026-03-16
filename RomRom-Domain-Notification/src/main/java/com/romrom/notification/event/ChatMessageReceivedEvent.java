package com.romrom.notification.event;

import java.util.UUID;
import lombok.Getter;

/**
 * 채팅 메시지 수신 알림
 * - Title: {상대방 닉네임}
 * - Body: {메시지 내용}
 */
@Getter
public class ChatMessageReceivedEvent extends NotificationEvent {

  private final UUID chatRoomId;
  private final String senderNickname;
  private final String content;

  public ChatMessageReceivedEvent(
    UUID targetMemberId,
    UUID chatRoomId,
    String senderNickname,
    String content
  ) {
    super(targetMemberId, NotificationType.CHAT_MESSAGE_RECEIVED);
    this.chatRoomId = chatRoomId;
    this.senderNickname = senderNickname;
    this.content = content;
    addPayload("chatRoomId", chatRoomId);
    addPayload("senderNickname", senderNickname);
    setDeepLink("romrom://chat/room?chatRoomId=" + chatRoomId);
  }

  @Override
  public String getTitle() {
    return String.format(getNotificationType().getTitle(), senderNickname);
  }

  @Override
  public String getBody() {
    return String.format(getNotificationType().getBody(), content);
  }
}
