package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ItemStatus {
  AVAILABLE("교환 가능"),
  EXCHANGED("교환 완료");

  private final String description;
}
