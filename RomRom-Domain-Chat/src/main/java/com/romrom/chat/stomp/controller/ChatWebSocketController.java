package com.romrom.chat.stomp.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import static com.romrom.chat.stomp.interceptor.CustomChannelInterceptor.SESSION_USER_KEY;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

  private final ChatMessageService chatMessageService;

  // 클라: /app/chat.send
  // 서버: /exchange/chat.exchange/chat.room.{roomId}
  // WebsocketConfig 에서 설정한 applicationDestinationPrefixes("/app")가 붙음
  @MessageMapping("/chat.send")
  public void send(ChatMessagePayload payload, StompHeaderAccessor accessor) {
    CustomUserDetails customUserDetails = (CustomUserDetails) accessor.getSessionAttributes().get(SESSION_USER_KEY);
    // 추가 검증 및 메시지 저장, 이후 이벤트 리스너 호출
    chatMessageService.saveMessage(payload, customUserDetails);
  }
}
