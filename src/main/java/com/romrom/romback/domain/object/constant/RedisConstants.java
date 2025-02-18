package com.romrom.romback.domain.object.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RedisConstants {

  BLACKLIST_PREFIX("BL:"),

  BLACKLISTED_STATUS("blacklisted"),

  REFRESH_KEY_PREFIX("RT:");

  private final String value;
}
