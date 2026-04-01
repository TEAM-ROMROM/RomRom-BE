package com.romrom.chat.entity.mongo;

import java.util.EnumSet;
import java.util.Set;

public enum MessageType {
  TEXT,
  IMAGE,
  LOCATION,
  SYSTEM,
  TRADE_COMPLETE_REQUEST,
  TRADE_COMPLETE_REQUEST_CANCELED,
  TRADE_COMPLETE_REQUEST_REJECTED,
  TRADE_COMPLETED,
  ;

  public boolean isClientSendable() {
    return this == TEXT || this == IMAGE || this == LOCATION;
  }

  public boolean isTradeCompletionType() {
    return TRADE_COMPLETION_TYPES.contains(this);
  }

  public static Set<MessageType> tradeCompletionTypes() {
    return TRADE_COMPLETION_TYPES;
  }

  private static final Set<MessageType> TRADE_COMPLETION_TYPES = EnumSet.of(
      TRADE_COMPLETE_REQUEST,
      TRADE_COMPLETE_REQUEST_CANCELED,
      TRADE_COMPLETE_REQUEST_REJECTED,
      TRADE_COMPLETED
  );
}
