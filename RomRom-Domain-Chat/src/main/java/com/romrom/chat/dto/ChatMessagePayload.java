package com.romrom.chat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.romrom.chat.entity.mongo.MessageType;
import lombok.*;

@ToString
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
  private LocalDateTime sentAt;

}