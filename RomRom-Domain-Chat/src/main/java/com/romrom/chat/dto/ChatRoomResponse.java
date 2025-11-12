package com.romrom.chat.dto;

import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.item.entity.postgres.Item;
import lombok.*;
import org.springframework.data.domain.Page;

import java.util.Map;
import java.util.UUID;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatRoomResponse {
  private ChatRoom chatRoom;
  private Page<ChatMessage> messages;
  private Page<ChatRoomDetailDto> chatRoomDetailDtoPage;
}
