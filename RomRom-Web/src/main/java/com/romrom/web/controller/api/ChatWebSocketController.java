package com.romrom.web.controller.api;

import static com.romrom.chat.stomp.interceptor.CustomChannelInterceptor.SESSION_USER_KEY;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@RequiredArgsConstructor
@Tag(
    name = "채팅 웹소켓 전용 API",
    description = "채팅 관련 웹소켓 API 명세 제공"
)
@Slf4j
public class ChatWebSocketController implements ChatWebSocketControllerDocs {

  private final ChatMessageService chatMessageService;

  // 클라: /app/chat.send
  // 서버: /exchange/chat.exchange/chat.room.{roomId}
  // WebsocketConfig 에서 설정한 applicationDestinationPrefixes("/app")가 붙음
  @MessageMapping("/chat.send")
  public void send(ChatMessageRequest request, StompHeaderAccessor accessor) {
    CustomUserDetails customUserDetails = (CustomUserDetails) accessor.getSessionAttributes().get(SESSION_USER_KEY);
    if (customUserDetails == null) {
      log.error("세션에서 사용자 정보를 찾을 수 없습니다. 인증된 사용자가 아닙니다.");
      throw new CustomException(ErrorCode.UNAUTHORIZED);
    }
    customUserDetails.validateExpiration();
    chatMessageService.saveAndSendMessage(request, customUserDetails);
  }
  @Override
  @GetMapping("/chat-guide")
  public void getChatWebSocketInfo() {
    // 문서화용 가짜 메서드
  }
}
