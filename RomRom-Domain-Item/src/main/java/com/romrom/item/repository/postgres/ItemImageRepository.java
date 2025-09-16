package com.romrom.item.repository.postgres;

import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

  List<ItemImage> findAllByItem(Item item);

  List<ItemImage> findAllByItem_ItemIdIn(List<UUID> itemIds);

  List<ItemImage> findAllByItemIn(List<Item> items);

  void deleteByItemItemId(UUID itemId);

  void deleteAllByItem(Item item);

  List<ItemImage> findByItemAndFilePathIn(Item item, List<String> filePaths);

  /**
   * 특정 아이템(giveItem)으로 보낸 요청 중, 지정된 상태가 아닌 요청들을 페이징하여 조회합니다.
   */
  Page<TradeRequestHistory> findByGiveItemAndTradeStatusNot(Item giveItem, TradeStatus tradeStatus, Pageable pageable);

  /**
   * 특정 아이템(takeItem)으로 받은 요청 중, 지정된 상태가 아닌 요청들을 페이징하여 조회합니다.
   */
  Page<TradeRequestHistory> findByTakeItemAndTradeStatusNot(Item takeItem, TradeStatus tradeStatus, Pageable pageable);

}
