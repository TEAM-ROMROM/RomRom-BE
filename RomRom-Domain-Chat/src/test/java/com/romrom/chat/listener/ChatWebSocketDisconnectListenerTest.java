package com.romrom.chat.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.chat.stomp.interceptor.CustomChannelInterceptor;
import com.romrom.chat.stomp.presence.ChatSessionPresenceRegistry;
import com.romrom.member.entity.Member;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 연결 종료 퇴장 분기 검증 (#755): 단일 기기 > 전체 퇴장, 다른 기기 연결 중 > 끊긴 기기의 방만 퇴장, 중복 이벤트 무시
 */
class ChatWebSocketDisconnectListenerTest {

  private final ChatRoomService chatRoomService = mock(ChatRoomService.class);
  private final ChatSessionPresenceRegistry registry = new ChatSessionPresenceRegistry();
  private final ChatWebSocketDisconnectListener listener = new ChatWebSocketDisconnectListener(chatRoomService, registry);

  private final UUID memberId = UUID.randomUUID();
  private final UUID chatRoomIdX = UUID.randomUUID();

  @Test
  @DisplayName("다른 기기 연결 없음: 활성 방 전체 퇴장 (기존 동작 유지)")
  void 단일_기기는_전체_퇴장() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);

    listener.handle(disconnectEvent("phone"));

    verify(chatRoomService).leaveActiveChatRooms(memberId);
    verify(chatRoomService, never()).leaveActiveChatRooms(any(UUID.class), anySet());
  }

  @Test
  @DisplayName("다른 기기 연결 중: 끊긴 기기가 보던 방만 퇴장")
  void 다른_기기_연결_중이면_그_방만_퇴장() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);

    listener.handle(disconnectEvent("phone"));

    verify(chatRoomService).leaveActiveChatRooms(memberId, Set.of(chatRoomIdX));
    verify(chatRoomService, never()).leaveActiveChatRooms(memberId);
  }

  @Test
  @DisplayName("목록 화면 기기 종료: 다른 기기가 보던 방 유지")
  void 목록_화면_기기_종료는_방_유지() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);

    listener.handle(disconnectEvent("tablet"));

    verify(chatRoomService).leaveActiveChatRooms(memberId, Set.of());
    verify(chatRoomService, never()).leaveActiveChatRooms(memberId);
  }

  @Test
  @DisplayName("같은 세션 종료 이벤트 중복: 두 번째는 퇴장 처리 없음 (전체 퇴장으로 되돌아가지 않음)")
  void 중복_종료_이벤트는_무시() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);

    listener.handle(disconnectEvent("tablet"));
    listener.handle(disconnectEvent("tablet"));

    verify(chatRoomService, never()).leaveActiveChatRooms(memberId);
  }

  private SessionDisconnectEvent disconnectEvent(String sessionId) {
    Member member = Member.builder().memberId(memberId).build();
    Map<String, Object> sessionAttributes = new HashMap<>();
    sessionAttributes.put(CustomChannelInterceptor.SESSION_USER_KEY, new CustomUserDetails(member));

    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    accessor.setSessionId(sessionId);
    accessor.setSessionAttributes(sessionAttributes);
    Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    return new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);
  }
}
