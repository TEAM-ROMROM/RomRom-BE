package com.romrom.chat.listener;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.chat.stomp.interceptor.CustomChannelInterceptor;
import com.romrom.chat.stomp.presence.ChatSessionPresenceRegistry;
import com.romrom.chat.stomp.presence.ChatSessionPresenceRegistry.ChatSessionDisconnectResult;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 웹소켓 연결 종료 > 채팅방 퇴장 처리
 * 다른 기기 연결 없음: 활성 방 전체 퇴장 / 다른 기기 연결 중: 이 기기만 보던 방만 퇴장 (#755)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketDisconnectListener {

  private final ChatRoomService chatRoomService;
  private final ChatSessionPresenceRegistry chatSessionPresenceRegistry;

  @EventListener
  public void handle(SessionDisconnectEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    // 세션 기록 정리 > 이미 처리된 세션이면 종료 (종료 이벤트는 한 세션에 여러 번 발생 가능)
    ChatSessionDisconnectResult disconnectResult = chatSessionPresenceRegistry.removeSession(event.getSessionId());
    if (disconnectResult == null) {
      log.debug("이미 처리됐거나 인증 전 종료된 웹소켓 세션입니다. sessionId={}", event.getSessionId());
      return;
    }

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
      if (disconnectResult.hasOtherLiveSession()) {
        chatRoomService.leaveActiveChatRooms(memberId, disconnectResult.chatRoomIdsToLeave());
        return;
      }
      chatRoomService.leaveActiveChatRooms(memberId);
    } catch (Exception e) {
      log.error("웹소켓 연결 종료 중 active 채팅방 퇴장 처리 실패. memberId={}, error={}",
          memberId, e.getMessage(), e);
    }
  }
}
