package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import java.time.LocalDateTime;
import lombok.*;

import java.util.List;
import java.util.UUID;

@ToString(exclude = "content")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatMessagePayload {
  private String chatMessageId;
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private Double latitude;
  private Double longitude;
  private MessageType type;
  private List<String> imageUrls;
  private LocalDateTime createdDate;
  private Boolean isProfanityDetected;
  // 보낸 사람 삭제 여부 (옛 앱은 모르는 필드라 무시)
  @JsonProperty("isDeleted")
  private Boolean isDeleted;
  // 약속 카드 (TEXT 메시지에만, 옛 앱은 content 글자로 표시)
  private ChatAppointment appointment;

  public static ChatMessagePayload from(ChatMessage chatMessage) {
    return from(chatMessage, false);
  }

  public static ChatMessagePayload from(ChatMessage chatMessage, Boolean isProfanityDetected) {
    return ChatMessagePayload.builder()
        .chatMessageId(chatMessage.getChatMessageId())
        .chatRoomId(chatMessage.getChatRoomId())
        .senderId(chatMessage.getSenderId())
        .recipientId(chatMessage.getRecipientId())
        .content(chatMessage.getContent())
        .latitude(chatMessage.getLatitude())
        .longitude(chatMessage.getLongitude())
        .type(chatMessage.getType())
        .imageUrls(chatMessage.getImageUrls())
        .createdDate(chatMessage.getCreatedDate())
        .isProfanityDetected(isProfanityDetected)
        .isDeleted(chatMessage.isDeleted())
        .appointment(chatMessage.getAppointment())
        .build();
  }
}
