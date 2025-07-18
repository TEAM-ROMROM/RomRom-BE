package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeStatus {
  PENDING("대기"),
  ACCEPTED("승인"),
  CANCELED("취소");

  private final String description;
}
