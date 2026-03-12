package com.romrom.chat.dto;

import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
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
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private MessageType type;
  private List<String> imageUrls;

  public static ChatMessagePayload from(ChatMessage chatMessage) {
    return ChatMessagePayload.builder()
        .chatRoomId(chatMessage.getChatRoomId())
        .senderId(chatMessage.getSenderId())
        .recipientId(chatMessage.getRecipientId())
        .content(chatMessage.getContent())
        .type(chatMessage.getType())
        .imageUrls(chatMessage.getImageUrls())
        .build();
  }
}