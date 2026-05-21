package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LoginResult {
  SUCCESS("성공"),
  FAIL("실패");

  private final String description;
}
