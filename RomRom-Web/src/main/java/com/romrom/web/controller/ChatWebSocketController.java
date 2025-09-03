package com.romrom.web.controller;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.service.ChatMessageService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

import static com.romrom.chat.stomp.interceptor.CustomChannelInterceptor.SESSION_USER_KEY;


@Controller
@RequiredArgsConstructor
@Tag(
    name = "채팅 웹소켓 전용 API",
    description = "채팅 관련 웹소켓 API 명세 제공"
)
public class ChatWebSocketController implements ChatWebSocketControllerDocs {

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
  @Override
  @GetMapping("/chat-guide")
  public void getChatWebSocketInfo() {
    // 문서화용 가짜 메서드
  }
}
