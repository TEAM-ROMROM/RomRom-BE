package com.romrom.chat.listener;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.chat.stomp.interceptor.CustomChannelInterceptor;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketDisconnectListener {

  private final ChatRoomService chatRoomService;

  @EventListener
  public void handle(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
    if (sessionAttributes == null) {
      log.debug("웹소켓 연결 종료 처리 중 세션 속성을 찾을 수 없습니다. sessionId={}", accessor.getSessionId());
      return;
    }

    Object sessionUser = sessionAttributes.get(CustomChannelInterceptor.SESSION_USER_KEY);
    if (!(sessionUser instanceof CustomUserDetails customUserDetails)) {
      log.debug("웹소켓 연결 종료 처리 중 세션 사용자를 찾을 수 없습니다. sessionId={}", accessor.getSessionId());
      return;
    }

    UUID memberId = customUserDetails.getMember().getMemberId();
    try {
      chatRoomService.leaveActiveChatRooms(memberId);
    } catch (Exception e) {
      log.error("웹소켓 연결 종료 중 active 채팅방 퇴장 처리 실패. memberId={}, error={}",
          memberId, e.getMessage(), e);
    }
  }
}
