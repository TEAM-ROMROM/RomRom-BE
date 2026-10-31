package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SocialPlatform {
  NORMAL("기본"),
  KAKAO("카카오"),
  GOOGLE("구글"),
  APPLE("애플"),
  ADMIN("관리자");

  private final String description;
}
