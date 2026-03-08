package com.romrom.chat.dto;

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
public class ChatMessageResponse {
  private UUID chatRoomId;
  private UUID senderId;
  private UUID recipientId;
  private String content;
  private MessageType type;
  private List<String> imageUrls;
}