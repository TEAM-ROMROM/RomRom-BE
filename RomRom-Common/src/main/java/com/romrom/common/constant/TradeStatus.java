package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TradeStatus {
  PENDING("대기"),
  TRADED("거래 완료"),
  CANCELED("취소"),
  CHATTING("채팅중"),
  TRADE_COMPLETE_REQUESTED("교환 완료 요청중"),
  // ORDINAL 저장(5) > 끝에만 추가. 기본 목록 응답에서 제외 (구버전 앱 미인식, #859)
  REJECTED("거절"),
  ;

  private final String description;
}
