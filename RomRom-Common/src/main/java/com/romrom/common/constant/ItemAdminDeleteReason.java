package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ItemAdminDeleteReason {
  PROHIBITED_ITEM(1, "거래 금지 품목"),
  FRAUD_SUSPECTED(2, "사기 의심"),
  INAPPROPRIATE_CONTENT(3, "부적절한 콘텐츠"),
  COPYRIGHT_VIOLATION(4, "저작권 침해"),
  REPORT_ACCUMULATED(5, "신고 누적"),
  ETC(6, "기타");

  private final int code;
  private final String description;
}
