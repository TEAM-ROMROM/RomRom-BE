package com.romrom.common.constant;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AccountStatus {
  ACTIVE_ACCOUNT("활성화된 계정"),
  DELETE_ACCOUNT("삭제된 계정"),
  TEST_ACCOUNT("테스트 계정"),
  SUSPENDED_ACCOUNT("정지된 계정");

  private final String description;

  public static final LocalDateTime PERMANENT_SUSPENSION_UNTIL = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
}
