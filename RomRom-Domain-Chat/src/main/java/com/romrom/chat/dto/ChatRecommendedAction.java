package com.romrom.chat.dto;

public enum ChatRecommendedAction {
  NONE,
  SEND_LOCATION,
  REQUEST_TRADE_COMPLETION,
  CANCEL_TRADE_COMPLETION_REQUEST,
  REJECT_TRADE_COMPLETION_REQUEST,
  CONFIRM_TRADE_COMPLETION,
  ;

  public boolean isNone() {
    return this == NONE;
  }
}
