package com.romrom.chat.dto;

import java.util.UUID;

import com.romrom.chat.entity.mongo.MessageType;
import lombok.*;

@ToString(exclude = "content")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatMessagePayload {
  private UUID chatRoomId;
  private String content;
  private MessageType type;
}