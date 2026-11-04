package com.romrom.item.dto;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemSearchSortField;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 물품 검색 조건: 조회 회원, 정리된 검색어, 카테고리(비면 전체), 정렬, 거리순 기준 좌표(없으면 null), 제외 물품 ID(신고 물품)
 */
public record ItemSearchCondition(
    UUID viewerMemberId,
    String keyword,
    List<ItemCategory> itemCategories,
    ItemSearchSortField sortField,
    Double longitude,
    Double latitude,
    Collection<UUID> excludedItemIds
) {

  /**
   * 거리순 기준 좌표 보유 여부
   */
  public boolean hasLocation() {
    return longitude != null && latitude != null;
  }
}
