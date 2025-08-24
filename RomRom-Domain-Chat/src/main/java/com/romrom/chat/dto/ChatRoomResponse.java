package com.romrom.chat.dto;

import com.romrom.chat.entity.mongo.ChatMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.UUID;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatRoomResponse {
  private UUID roomId;
  private Page<ChatMessage> messages;
}
