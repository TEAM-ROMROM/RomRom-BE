package com.romrom.chat.dto;

import com.romrom.member.entity.Member;
import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class ChatRoomMemberInfo {
  private final UUID memberId;
  private final String nickname;
  private final Double latitude;
  private final Double longitude;

  public static ChatRoomMemberInfo from(Member member) {
    return ChatRoomMemberInfo.builder()
        .memberId(member.getMemberId())
        .nickname(member.getNickname())
        .latitude(member.getLatitude())
        .longitude(member.getLongitude())
        .build();
  }
}
