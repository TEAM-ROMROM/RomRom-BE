package com.romrom.notification.event;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 거래 진행 알림 단위 테스트 (#861): 타입·대상·문구·딥링크, 옛 앱 숨김 타입
 */
class TradeProgressNotificationEventTest {

  private static final UUID TARGET_MEMBER_ID = UUID.randomUUID();
  private static final UUID TRADE_REQUEST_HISTORY_ID = UUID.randomUUID();
  private static final UUID CHAT_ROOM_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();

  @Test
  @DisplayName("수락: 채팅방 딥링크, 닉네임·물품명 본문")
  void accepted_채팅방딥링크() {
    TradeProgressNotificationEvent event = TradeProgressNotificationEvent.accepted(
        TARGET_MEMBER_ID, TRADE_REQUEST_HISTORY_ID, CHAT_ROOM_ID, "롬롬", "자전거");

    Assertions.assertEquals(NotificationType.TRADE_REQUEST_ACCEPTED, event.getNotificationType());
    Assertions.assertEquals(TARGET_MEMBER_ID, event.getTargetMemberId());
    Assertions.assertEquals("롬롬님이 '자전거' 교환 요청을 수락했어요. 채팅을 시작해 보세요!", event.getBody());
    Assertions.assertEquals("romrom://chat/room?chatRoomId=" + CHAT_ROOM_ID, event.getPayload().get("deepLink"));
    Assertions.assertEquals("TRADE_REQUEST_ACCEPTED", event.getPayload().get("notificationType"));
    Assertions.assertEquals(TRADE_REQUEST_HISTORY_ID.toString(), event.getPayload().get("tradeRequestHistoryId"));
  }

  @Test
  @DisplayName("거절: 상대 물품 상세 딥링크")
  void rejected_물품상세딥링크() {
    TradeProgressNotificationEvent event = TradeProgressNotificationEvent.rejected(
        TARGET_MEMBER_ID, TRADE_REQUEST_HISTORY_ID, ITEM_ID, "자전거");

    Assertions.assertEquals(NotificationType.TRADE_REQUEST_REJECTED, event.getNotificationType());
    Assertions.assertEquals("romrom://item/detail?itemId=" + ITEM_ID, event.getPayload().get("deepLink"));
    Assertions.assertTrue(event.getBody().contains("자전거"));
  }

  @Test
  @DisplayName("완료: 채팅방 경로 / 채팅 없는 경로 딥링크")
  void completed_경로별딥링크() {
    TradeProgressNotificationEvent inChatRoom = TradeProgressNotificationEvent.completedInChatRoom(
        TARGET_MEMBER_ID, TRADE_REQUEST_HISTORY_ID, CHAT_ROOM_ID, "롬롬");
    TradeProgressNotificationEvent withoutChatRoom = TradeProgressNotificationEvent.completedWithoutChatRoom(
        TARGET_MEMBER_ID, TRADE_REQUEST_HISTORY_ID, ITEM_ID, "롬롬");

    Assertions.assertEquals(NotificationType.TRADE_COMPLETED, inChatRoom.getNotificationType());
    Assertions.assertEquals("romrom://chat/room?chatRoomId=" + CHAT_ROOM_ID, inChatRoom.getPayload().get("deepLink"));
    Assertions.assertEquals("romrom://item/detail?itemId=" + ITEM_ID, withoutChatRoom.getPayload().get("deepLink"));
    Assertions.assertEquals("교환이 완료됐어요", inChatRoom.getTitle());
  }

  @Test
  @DisplayName("후기: 받은 후기 딥링크")
  void reviewReceived_받은후기딥링크() {
    TradeProgressNotificationEvent event = TradeProgressNotificationEvent.reviewReceived(
        TARGET_MEMBER_ID, TRADE_REQUEST_HISTORY_ID, "롬롬");

    Assertions.assertEquals(NotificationType.TRADE_REVIEW_RECEIVED, event.getNotificationType());
    Assertions.assertEquals("romrom://profile/reviews", event.getPayload().get("deepLink"));
  }

  @Test
  @DisplayName("옛 앱 숨김 타입: 확장 4종 + 키워드 알림(#873)만, 기존 5종 제외")
  void legacyClientHiddenTypes_확장4종() {
    Set<NotificationType> hiddenTypes = NotificationType.legacyClientHiddenTypes();

    Assertions.assertEquals(Set.of(
        NotificationType.TRADE_REQUEST_ACCEPTED,
        NotificationType.TRADE_REQUEST_REJECTED,
        NotificationType.TRADE_COMPLETED,
        NotificationType.TRADE_REVIEW_RECEIVED,
        NotificationType.KEYWORD_ITEM_REGISTERED
    ), hiddenTypes);
    Assertions.assertFalse(hiddenTypes.contains(NotificationType.TRADE_REQUEST_RECEIVED));
  }

  @Test
  @DisplayName("공지 푸시: 공지 상세 딥링크")
  void announcement_공지상세딥링크() {
    UUID announcementId = UUID.randomUUID();
    AnnouncementEvent event = new AnnouncementEvent(announcementId, "점검 안내");

    Assertions.assertEquals("romrom://notice/detail?announcementId=" + announcementId, event.getPayload().get("deepLink"));
  }
}
