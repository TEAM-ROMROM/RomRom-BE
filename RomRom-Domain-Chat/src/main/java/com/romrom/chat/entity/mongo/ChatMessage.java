package com.romrom.chat.entity.mongo;

import java.util.UUID;

import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
@CompoundIndex(def = "{'chatRoomId': 'createdDate' 'createdDate': -1}")
public class ChatMessage extends BaseMongoEntity {
  @Id
  private String chatMessageId;
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private MessageType type;

  public static ChatMessage fromPayload(ChatMessagePayload payload) {
    return ChatMessage.builder()
        .chatRoomId(payload.getChatRoomId())
        .senderId(payload.getSenderId())
        .recipientId(payload.getRecipientId())
        .content(payload.getContent())
        .type(payload.getType())
        .build();
  }
}