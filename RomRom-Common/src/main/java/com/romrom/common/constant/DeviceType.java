package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DeviceType {
  ANDROID("안드로이드"),
  IOS("애플"),
  OTHER("기타");

  private final String description;
}