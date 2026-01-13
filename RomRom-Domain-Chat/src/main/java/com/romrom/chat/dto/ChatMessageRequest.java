package com.romrom.chat.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.romrom.chat.entity.mongo.MessageType;
import lombok.*;

@ToString(exclude = {"content", "imageUrls"})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatMessageRequest {
  private UUID chatRoomId;
  private String content;
  private MessageType type;
  @Builder.Default
  private List<String> imageUrls = new ArrayList<>();
}