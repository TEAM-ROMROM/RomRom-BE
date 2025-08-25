package com.romrom.chat.stomp.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import com.romrom.chat.service.ChatService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

  private final SimpMessagingTemplate template;
  private final ChatRoutingProperties chatRoutingProperties;
  private final ChatService chatService;

  // 클라: /app/chat.send
  @MessageMapping("/chat.send")
  public void send(ChatMessagePayload payload, Principal principal) {
    CustomUserDetails customUserDetails = (CustomUserDetails) principal;

    // 1) 보낸이 검증
    if (!customUserDetails.getMemberId().equals(payload.getSenderId())) {
      throw new CustomException(ErrorCode.INVALID_SENDER);
    }

    // 2) roomId 검증
    UUID chatRoomId = payload.getChatRoomId();
    chatService.assertAccessible(chatRoomId);

    // 3) Mongo 저장
    ChatMessage saved = chatService.saveMessage(chatRoomId, payload);

    // 4) 브로커로 송출 (1대1 채팅방 채널로만, 개인 채널은 우선 보류)
    ChatMessagePayload outgoing = ChatMessagePayload.builder()
        .chatRoomId(chatRoomId)
        .senderId(saved.getSenderId())
        .recipientId(saved.getRecipientId())
        .content(saved.getContent())
        .type(saved.getType())
        .sentAt(saved.getCreatedDate())
        .build();

    String roomRoutingKey = "chat.room." + chatRoomId;

    template.convertAndSend("/exchange/" + chatRoutingProperties.chatExchange() + "/" + roomRoutingKey, outgoing);
  }
}
