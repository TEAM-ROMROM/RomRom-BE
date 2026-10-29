package com.romrom.chat.stomp.presence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.chat.stomp.presence.ChatSessionPresenceRegistry.ChatSessionDisconnectResult;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 세션별 열람 방 추적 검증 (#755): 단일 기기, 다른 기기 연결 중, 같은 방 동시 열람, 구독 해제, 읽음 외 채널
 */
class ChatSessionPresenceRegistryTest {

  private final ChatSessionPresenceRegistry registry = new ChatSessionPresenceRegistry();
  private final UUID memberId = UUID.randomUUID();
  private final UUID chatRoomIdX = UUID.randomUUID();
  private final UUID chatRoomIdY = UUID.randomUUID();

  @Test
  @DisplayName("다른 기기 없음: hasOtherLiveSession false (활성 방 전체 퇴장 경로)")
  void 단일_기기_종료는_다른_세션_없음() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);

    ChatSessionDisconnectResult disconnectResult = registry.removeSession("phone");

    assertFalse(disconnectResult.hasOtherLiveSession());
    assertEquals(Set.of(chatRoomIdX), disconnectResult.chatRoomIdsToLeave());
  }

  @Test
  @DisplayName("방을 안 보던 기기가 끊기면 다른 기기가 보던 방은 퇴장 대상 아님")
  void 목록_화면_기기_종료는_퇴장_대상_없음() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);
    registry.registerSubscription("tablet", "sub-0", "/sub/chat.room." + chatRoomIdX);

    ChatSessionDisconnectResult disconnectResult = registry.removeSession("tablet");

    assertTrue(disconnectResult.hasOtherLiveSession());
    assertTrue(disconnectResult.chatRoomIdsToLeave().isEmpty());
  }

  @Test
  @DisplayName("방을 보던 기기가 끊기면 그 방만 퇴장 대상 (다른 기기 연결 유지)")
  void 방_보던_기기_종료는_그_방만_퇴장() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/exchange/chat.exchange/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);
    registry.registerSubscription("tablet", "sub-1", "/sub/chat.read." + chatRoomIdY);

    ChatSessionDisconnectResult disconnectResult = registry.removeSession("phone");

    assertTrue(disconnectResult.hasOtherLiveSession());
    assertEquals(Set.of(chatRoomIdX), disconnectResult.chatRoomIdsToLeave());
  }

  @Test
  @DisplayName("두 기기가 같은 방을 보면 한쪽이 끊겨도 퇴장 대상 아님")
  void 같은_방_동시_열람은_유지() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.registerSession("tablet", memberId);
    registry.registerSubscription("tablet", "sub-0", "/sub/chat.read." + chatRoomIdX);

    ChatSessionDisconnectResult disconnectResult = registry.removeSession("phone");

    assertTrue(disconnectResult.chatRoomIdsToLeave().isEmpty());
  }

  @Test
  @DisplayName("구독 해제한 방, 다른 회원 세션은 계산에서 제외")
  void 구독_해제와_다른_회원은_제외() {
    registry.registerSession("phone", memberId);
    registry.registerSubscription("phone", "sub-0", "/sub/chat.read." + chatRoomIdX);
    registry.removeSubscription("phone", "sub-0");
    registry.registerSession("other-member", UUID.randomUUID());

    ChatSessionDisconnectResult disconnectResult = registry.removeSession("phone");

    assertFalse(disconnectResult.hasOtherLiveSession());
    assertTrue(disconnectResult.chatRoomIdsToLeave().isEmpty());
  }

  @Test
  @DisplayName("미등록·중복 종료 세션은 null")
  void 미등록_세션은_null() {
    registry.registerSession("phone", memberId);
    registry.removeSession("phone");

    assertNull(registry.removeSession("phone"));
    assertNull(registry.removeSession("unknown"));
  }

  @Test
  @DisplayName("읽음 채널 주소만 채팅방 ID 추출")
  void 읽음_채널_주소만_추출() {
    assertEquals(chatRoomIdX, ChatSessionPresenceRegistry.extractReadChannelChatRoomId("/sub/chat.read." + chatRoomIdX));
    assertNull(ChatSessionPresenceRegistry.extractReadChannelChatRoomId("/sub/chat.room." + chatRoomIdX));
    assertNull(ChatSessionPresenceRegistry.extractReadChannelChatRoomId("/sub/chat.read.not-a-uuid"));
    assertNull(ChatSessionPresenceRegistry.extractReadChannelChatRoomId(null));
  }
}
