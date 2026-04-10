package com.romrom.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChatActionRecommendationPayload {
  private UUID chatRoomId;
  private UUID targetMemberId;
  private ChatRecommendedAction action;
  private String reason;
  private String basedOnMessageId;
  private LocalDateTime createdDate;

  public static ChatActionRecommendationPayload none(UUID chatRoomId, UUID targetMemberId, String basedOnMessageId) {
    return ChatActionRecommendationPayload.builder()
        .chatRoomId(chatRoomId)
        .targetMemberId(targetMemberId)
        .action(ChatRecommendedAction.NONE)
        .basedOnMessageId(basedOnMessageId)
        .createdDate(LocalDateTime.now())
        .build();
  }
}
