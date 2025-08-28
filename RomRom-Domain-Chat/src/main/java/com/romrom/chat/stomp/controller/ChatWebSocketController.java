package com.romrom.chat.stomp.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.service.ChatService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

  private final ChatService chatService;

  // 클라: /app/chat.send
  // 서버: /exchange/chat.exchange/chat.room.{roomId}
  // WebsocketConfig 에서 설정한 applicationDestinationPrefixes("/app")가 붙음
  @MessageMapping("/chat.send")
  public void send(ChatMessagePayload payload, StompHeaderAccessor accessor) {
    CustomUserDetails customUserDetails = (CustomUserDetails) accessor.getSessionAttributes().get("user");

    // 보낸이 검증
    if (!customUserDetails.getMemberId().equals(payload.getSenderId().toString())) { // UUID to String 처리 필요
      log.error("보낸 이가 올바르지 않습니다. payload senderId: {}, principal memberId: {}",
          payload.getSenderId(), customUserDetails.getMemberId());
      throw new CustomException(ErrorCode.INVALID_SENDER);
    }

    // 추가 검증 및 메시지 저장, 이후 이벤트 리스너 호출
    chatService.saveMessage(payload);
  }
}
