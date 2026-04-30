package com.romrom.common.constant;

import lombok.AllArgsConstructor;

/**
 * 거래 요청 목록(요청한/요청받은) 조회 시 정렬 기준
 * - 정렬 방향(ASC/DESC)은 별도의 {@link org.springframework.data.domain.Sort.Direction} 으로 전달받는다.
 * - PRICE: 상대 물품 가격 기준 (받은 요청은 giveItem.price, 보낸 요청은 takeItem.price). 서비스에서 컨텍스트에 따라 실제 경로를 결정한다.
 * - AI_RECOMMENDED: 임베딩 유사도 기반 정렬. 정렬 방향은 무시되며 항상 유사도 순으로 정렬된다.
 */
@AllArgsConstructor
public enum TradeRequestSortField implements SortField {

  CREATED_DATE("createdDate"),       // 생성일 (최신/오래된 순)
  PRICE("price"),                    // 상대 물품 가격
  AI_RECOMMENDED("aiRecommended");   // AI 추천 (선호 카테고리 임베딩 유사도)

  private final String property;

  @Override
  public String getProperty() {
    return property;
  }
}
