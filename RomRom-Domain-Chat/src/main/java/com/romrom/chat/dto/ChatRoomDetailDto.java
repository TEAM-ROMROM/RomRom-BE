package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChatRoomDetailDto {
  private final UUID chatRoomId;

  private final boolean isBlocked;  // 내가 차단했을때 or 상대방이 날 차단했을때 true

  private final Member targetMember;

  private final String targetMemberEupMyeonDong;

  private final String lastMessageContent;
  private final LocalDateTime lastMessageTime;

  private final Long unreadCount;

  private final ChatRoomType chatRoomType;

  private final String targetItemImageUrl; // 상대방 물품의 대표 이미지 URL
  private final String myItemImageUrl;     // 내 물품의 대표 이미지 URL

  // 내가 이 방 알림을 껐는지 (끈 방도 안 읽음 수는 그대로)
  @JsonProperty("isNotificationMuted")
  private final Boolean isNotificationMuted;

  public static ChatRoomDetailDto from(UUID chatRoomId, boolean isBlocked, Member member, String targetMemberEupMyeonDong,
                                       Long unreadCount, String lastMessage, LocalDateTime lastMessageTime,
                                       ChatRoomType chatRoomType, String targetItemImageUrl, String myItemImageUrl,
                                       boolean isNotificationMuted) {
    return ChatRoomDetailDto.builder()
        .chatRoomId(chatRoomId)
        .isBlocked(isBlocked)
        .targetMemberEupMyeonDong(targetMemberEupMyeonDong)
        .targetMember(member)
        .lastMessageContent(lastMessage)
        .lastMessageTime(lastMessageTime)
        .unreadCount(unreadCount)
        .chatRoomType(chatRoomType)
        .targetItemImageUrl(targetItemImageUrl)
        .myItemImageUrl(myItemImageUrl)
        .isNotificationMuted(isNotificationMuted)
        .build();
  }
}
