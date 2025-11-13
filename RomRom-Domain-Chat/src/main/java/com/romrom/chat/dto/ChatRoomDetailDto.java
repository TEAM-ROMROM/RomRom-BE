package com.romrom.chat.dto;

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

  private final String targetMemberEupMyeonDong;

  private final String lastMessageContent;
  private final LocalDateTime lastMessageTime;

  private final Long unreadCount;

  private final ChatRoomType chatRoomType;

  public static ChatRoomDetailDto from(UUID chatRoomId, Member member, String targetMemberEupMyeonDong, Long unreadCount, String lastMessage, LocalDateTime lastMessageTime, ChatRoomType chatRoomType) {
    return ChatRoomDetailDto.builder()
        .chatRoomId(chatRoomId)
        .targetMemberEupMyeonDong(targetMemberEupMyeonDong)
        .targetMember(member)
        .lastMessageContent(lastMessage)
        .lastMessageTime(lastMessageTime)
        .unreadCount(unreadCount)
        .chatRoomType(chatRoomType)
        .build();
  }
}
