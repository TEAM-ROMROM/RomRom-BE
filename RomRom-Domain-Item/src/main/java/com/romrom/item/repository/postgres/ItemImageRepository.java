package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemImageRepository extends JpaRepository<ItemImage, UUID> {

  List<ItemImage> findAllByItem(Item item);

  List<ItemImage> findAllByItem_ItemIdIn(List<UUID> itemIds);

  void deleteByItemItemId(UUID itemId);

  void deleteAllByItem(Item item);

  List<ItemImage> findByItemAndFilePathIn(Item item, List<String> filePaths);
}
