package com.romrom.item.entity;

import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 예약중 상태 전이 단위 테스트 (#859): 채팅 시작 > 양쪽 예약, 최초 시각 유지, 교환 완료 > 해제
 */
class ItemReservationStateTest {

  @Test
  @DisplayName("채팅 시작: 두 물품 예약중, 이미 예약중이면 최초 시각 유지")
  void startChatting_reservesBothItemsAndKeepsFirstReservedAt() {
    LocalDateTime firstReservedAt = LocalDateTime.of(2026, 11, 1, 10, 0);
    Item takeItem = Item.builder().itemId(UUID.randomUUID()).itemStatus(ItemStatus.AVAILABLE)
        .reservedAt(firstReservedAt).build();
    Item giveItem = Item.builder().itemId(UUID.randomUUID()).itemStatus(ItemStatus.AVAILABLE).build();
    TradeRequestHistory history = TradeRequestHistory.builder()
        .takeItem(takeItem).giveItem(giveItem).tradeStatus(TradeStatus.PENDING).build();

    history.startChatting();

    Assertions.assertEquals(TradeStatus.CHATTING, history.getTradeStatus());
    Assertions.assertEquals(firstReservedAt, takeItem.getReservedAt());
    Assertions.assertNotNull(giveItem.getReservedAt());
    Assertions.assertTrue(takeItem.getIsReserved());
    Assertions.assertTrue(giveItem.getIsReserved());
  }

  @Test
  @DisplayName("교환 완료: EXCHANGED + 예약 해제, 교환완료 물품은 isReserved false")
  void completeTrade_releasesReservation() {
    Item takeItem = Item.builder().itemId(UUID.randomUUID()).itemStatus(ItemStatus.AVAILABLE)
        .reservedAt(LocalDateTime.now()).build();
    Item giveItem = Item.builder().itemId(UUID.randomUUID()).itemStatus(ItemStatus.AVAILABLE)
        .reservedAt(LocalDateTime.now()).build();
    TradeRequestHistory history = TradeRequestHistory.builder()
        .takeItem(takeItem).giveItem(giveItem).tradeStatus(TradeStatus.CHATTING).build();

    history.completeTrade();

    Assertions.assertNull(takeItem.getReservedAt());
    Assertions.assertFalse(takeItem.getIsReserved());
    Assertions.assertFalse(giveItem.getIsReserved());
  }

  @Test
  @DisplayName("isReserved: 예약 시각이 남아도 교환완료 물품은 false")
  void isReserved_falseWhenExchanged() {
    Item exchangedItem = Item.builder().itemStatus(ItemStatus.EXCHANGED).reservedAt(LocalDateTime.now()).build();
    Assertions.assertFalse(exchangedItem.getIsReserved());
  }
}
