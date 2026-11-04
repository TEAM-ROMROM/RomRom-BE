package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 물품 검색 정렬 기준 4개: 최신순, 거리순, 낮은 가격순, 높은 가격순 (홈 피드 ItemSortField와 별도)
 */
@Getter
@AllArgsConstructor
public enum ItemSearchSortField {
  LATEST("최신순"),
  DISTANCE("거리순"),
  PRICE_ASC("낮은 가격순"),
  PRICE_DESC("높은 가격순");

  private final String description;
}
