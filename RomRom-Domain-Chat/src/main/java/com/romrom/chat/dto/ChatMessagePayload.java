package com.romrom.chat.dto;

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
  private MessageType type;
  private List<String> imageUrls;
  private LocalDateTime createdDate;
  private Boolean isProfanityDetected;

  public static ChatMessagePayload from(ChatMessage chatMessage) {
    return ChatMessagePayload.builder()
        .chatMessageId(chatMessage.getChatMessageId())
        .chatRoomId(chatMessage.getChatRoomId())
        .senderId(chatMessage.getSenderId())
        .recipientId(chatMessage.getRecipientId())
        .content(chatMessage.getContent())
        .type(chatMessage.getType())
        .imageUrls(chatMessage.getImageUrls())
        .createdDate(chatMessage.getCreatedDate())
        .isProfanityDetected(false)
        .build();
  }

  public static ChatMessagePayload from(ChatMessage chatMessage, Boolean isProfanityDetected) {
    return ChatMessagePayload.builder()
        .chatMessageId(chatMessage.getChatMessageId())
        .chatRoomId(chatMessage.getChatRoomId())
        .senderId(chatMessage.getSenderId())
        .recipientId(chatMessage.getRecipientId())
        .content(chatMessage.getContent())
        .type(chatMessage.getType())
        .imageUrls(chatMessage.getImageUrls())
        .createdDate(chatMessage.getCreatedDate())
        .isProfanityDetected(isProfanityDetected)
        .build();
  }
}
