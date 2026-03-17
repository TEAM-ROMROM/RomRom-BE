package com.romrom.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import lombok.*;
import org.springframework.data.domain.Slice;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ChatRoomResponse {
  @JsonProperty("isOpponentDeleted")
  private boolean isOpponentDeleted;
  private ChatRoom chatRoom;
  private Slice<ChatMessage> messages;
  private Slice<ChatRoomDetailDto> chatRoomDetailDtoPage;
  private ChatUserState opponentState;
}
