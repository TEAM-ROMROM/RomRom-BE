package com.romrom.item.repository.postgres;

import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface TradeRequestHistoryRepository extends JpaRepository<TradeRequestHistory, UUID> {

  @Query("SELECT COUNT(t) > 0 FROM TradeRequestHistory t " +
      "WHERE t.tradeStatus IN (com.romrom.common.constant.TradeStatus.PENDING, " +
      "                         com.romrom.common.constant.TradeStatus.CHATTING, " +
      "                         com.romrom.common.constant.TradeStatus.TRADED) " +
      "  AND ((t.takeItem.itemId = :takeItemId AND t.giveItem.itemId = :giveItemId) OR (t.takeItem.itemId = :giveItemId AND t.giveItem.itemId = :takeItemId))")
  boolean existsTradeRequestBetweenItems(@Param("takeItemId") UUID takeItemId, @Param("giveItemId") UUID giveItemId);

  Optional<TradeRequestHistory> findByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  // 요청받은 내역 조회 (차단 필터링 추가)
  @Query(
    value = "SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.takeItem = :takeItem " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member) " +
      "       OR (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member)" +
      ")")
  Page<TradeRequestHistory> findByTakeItem(@Param("takeItem") Item takeItem, Pageable pageable);

  Page<TradeRequestHistory> findByGiveItemAndTradeStatus(Item giveItem, TradeStatus tradeStatus, Pageable pageable);

  // 요청한 내역 조회 (차단 필터링 추가)
  @Query("SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.giveItem = :giveItem " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member) " +
      "       OR (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member)" +
      ")")
  Page<TradeRequestHistory> findByGiveItem(Item giveItem, Pageable pageable);

  void deleteAllByTakeItemItemId(UUID itemId);

  void deleteAllByGiveItemItemId(UUID itemId);

  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.takeItem ti " +
      "JOIN FETCH t.giveItem gi " +
      "WHERE t.tradeRequestHistoryId = :tradeRequestHistoryId")
  Optional<TradeRequestHistory> findByTradeRequestHistoryIdWithItems(UUID tradeRequestHistoryId);
}
