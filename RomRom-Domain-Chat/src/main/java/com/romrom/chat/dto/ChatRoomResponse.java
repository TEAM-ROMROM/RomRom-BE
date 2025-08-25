package com.romrom.chat.dto;

import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
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
  private UUID chatRoomId;
  private Page<ChatMessage> messages;
  private Page<ChatRoom> chatRooms;
}
