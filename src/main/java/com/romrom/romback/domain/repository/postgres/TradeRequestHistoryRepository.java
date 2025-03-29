package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.constant.TradeStatus;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.TradeRequestHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRequestHistoryRepository extends JpaRepository<TradeRequestHistory, UUID> {

  boolean existsByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  Optional<TradeRequestHistory> findByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  Page<TradeRequestHistory> findByTakeItemAndTradeStatus(Item takeItem, TradeStatus tradeStatus, Pageable pageable);

  Page<TradeRequestHistory> findByGiveItemAndTradeStatus(Item giveItem, TradeStatus tradeStatus, Pageable pageable);
}
