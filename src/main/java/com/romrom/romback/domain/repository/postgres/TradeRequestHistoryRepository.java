package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.TradeRequestHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRequestHistoryRepository extends JpaRepository<TradeRequestHistory, UUID> {

  boolean existsByRequestedItemAndRequestingItem(Item requestedItem, Item requestingItem);

  Optional<TradeRequestHistory> findByRequestedItemAndRequestingItem(Item requestedItem, Item requestingItem);

  List<TradeRequestHistory> findByRequestedItem(Item requestedItem);

  List<TradeRequestHistory> findByRequestingItem(Item requestingItem);
}
