package com.romrom.item.repository.postgres;

import com.romrom.item.dto.ItemSearchCondition;
import com.romrom.item.entity.postgres.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ItemSearchRepository {

  /**
   * 물품 검색: 홈 피드 노출 조건 + 제외 물품 + 검색어(이름·설명) + 카테고리 > 정렬 기준별 정렬 (#862)
   */
  Page<Item> searchVisibleItems(ItemSearchCondition searchCondition, Pageable pageable);
}
