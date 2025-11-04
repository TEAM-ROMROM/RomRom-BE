package com.romrom.chat.dto;

import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChatRoomDetailDto {
  private final UUID chatRoomId;

  private final Member targetMember;

  private final String lastMessageContent;
  private final LocalDateTime lastMessageTime;

  private final Long unreadCount;

  public static ChatRoomDetailDto from(ChatRoom chatRoom, Member member, Long unreadCount, String lastMessage, LocalDateTime lastMessageTime) {
    return ChatRoomDetailDto.builder()
        .chatRoomId(chatRoom.getChatRoomId())
        .targetMember(member)
        .lastMessageContent(lastMessage)
        .lastMessageTime(lastMessageTime)
        .unreadCount(unreadCount)
        .build();
  }
}
