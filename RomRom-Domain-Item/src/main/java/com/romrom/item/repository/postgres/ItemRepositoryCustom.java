package com.romrom.item.repository.postgres;

import com.romrom.common.constant.SortDirection;
import com.romrom.common.constant.SortType;
import com.romrom.item.entity.postgres.Item;
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
      SortDirection sortDirection,
      Pageable pageable
  );
}
