package com.romrom.romback.domain.object.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JwtRedisType {

  BLACKLIST("BL:", "blacklisted"),

  REFRESH_KEY("RT:", "active");

  private final String prefix;
  private final String status;
}
