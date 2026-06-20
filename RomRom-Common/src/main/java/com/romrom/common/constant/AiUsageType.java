package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AiUsageType {
  PRICE_PREDICTION("가격예측"),
  UGC_FILTER("UGC필터"),
  IMAGE_ANALYSIS("이미지분석"),
  EMBEDDING("임베딩"),
  CATEGORY_MATCHING("카테고리매칭");

  private final String description;
}
