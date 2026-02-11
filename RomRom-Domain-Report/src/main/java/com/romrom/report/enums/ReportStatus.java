package com.romrom.report.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReportStatus {
  PENDING("대기중"),
  PROCESSING("처리중"),
  COMPLETED("완료"),
  REJECTED("반려");

  private final String description;
}
