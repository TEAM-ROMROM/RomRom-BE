package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemCondition {

  SEALED("미개봉"),

  SLIGHTLY_USED("사용감 적음"),

  MODERATELY_USED("사용감 적당함"),

  HEAVILY_USED("사용감 많음");

  private final String description;
}
