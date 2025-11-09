package com.romrom.item.repository.postgres;

import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface TradeRequestHistoryRepository extends JpaRepository<TradeRequestHistory, UUID> {

  boolean existsByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  Optional<TradeRequestHistory> findByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  Page<TradeRequestHistory> findByTakeItemAndTradeStatusIn(Item takeItem, List<TradeStatus> tradeStatuses, Pageable pageable);

  Page<TradeRequestHistory> findByGiveItemAndTradeStatus(Item giveItem, TradeStatus tradeStatus, Pageable pageable);

  Page<TradeRequestHistory> findByGiveItem(Item giveItem, Pageable pageable);

  void deleteAllByTakeItemItemId(UUID itemId);

  void deleteAllByGiveItemItemId(UUID itemId);

  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.takeItem ti " +
      "JOIN FETCH t.giveItem gi " +
      "WHERE t.tradeRequestHistoryId = :tradeRequestHistoryId")
  Optional<TradeRequestHistory> findByTradeRequestHistoryIdWithItems(UUID tradeRequestHistoryId);
}
