package com.romrom.chat.entity.mongo;

import java.util.UUID;

import com.romrom.common.entity.mongo.BaseMongoEntity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
public class ChatMessage extends BaseMongoEntity {
  @Id
  private String chatMessageId;
  private UUID roomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private MessageType type;
}