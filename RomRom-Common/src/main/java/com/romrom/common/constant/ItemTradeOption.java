package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemTradeOption {

  EXTRA_CHARGE("추가금"),

  DIRECT_ONLY("직거래만"),

  DELIVERY_ONLY("택배거래만");

  private final String description;
}
