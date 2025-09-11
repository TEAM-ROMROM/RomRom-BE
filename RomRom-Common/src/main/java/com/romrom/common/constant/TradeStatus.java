package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeStatus {
  PENDING("대기"),
  TRADED("거래 완료"),
  CANCELED("취소");

  private final String description;
}
