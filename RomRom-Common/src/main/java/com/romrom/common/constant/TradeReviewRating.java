package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeReviewRating {
  BAD("별로예요"),
  GOOD("좋아요"),
  GREAT("최고예요"),
  ;

  private final String description;
}
