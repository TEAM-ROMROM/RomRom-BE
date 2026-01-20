package com.romrom.chat.entity.mongo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
@CompoundIndex(def = "{'chatRoomId': 1, 'createdDate': -1}")
public class ChatMessage extends BaseMongoEntity {
  @Id
  private String chatMessageId;
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  @Builder.Default
  private List<String> imageUrls = new ArrayList<>();
  private MessageType type;

  public static ChatMessage fromChatMessageRequest(ChatMessageRequest request, UUID senderId, UUID recipientId) {
    return ChatMessage.builder()
        .chatRoomId(request.getChatRoomId())
        .senderId(senderId)
        .recipientId(recipientId)
        .content(request.getContent())
        .type(request.getType())
        .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>()) // null 방어 코드 추가
        .build();
  }
}