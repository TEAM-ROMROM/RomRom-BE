package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.SortType;
import com.romrom.item.entity.postgres.Item;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemRepositoryCustom {

  Page<Item> filterItems(
      UUID memberId,
      Double longitude,
      Double latitude,
      Double radius,
      float[] memberEmbedding,
      SortType sortType,
      Pageable pageable
  );

  Page<Item> findItemsForAdmin(
      String searchKeyword,
      ItemCategory itemCategory,
      ItemCondition itemCondition,
      ItemStatus itemStatus,
      Integer minPrice,
      Integer maxPrice,
      LocalDateTime startDate,
      LocalDateTime endDate,
      Pageable pageable
  );
}
