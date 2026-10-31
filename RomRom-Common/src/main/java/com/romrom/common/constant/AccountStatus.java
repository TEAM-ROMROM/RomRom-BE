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

  // 서버가 기한 없이 정지할 때 저장하는 값
  public static final LocalDateTime PERMANENT_SUSPENSION_UNTIL = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

  // 영구정지 판정 기준: suspendedUntil >= 이 값 (관리자 화면 저장값 2100-01-01, 서버 저장값 9999년 모두 포함)
  public static final LocalDateTime PERMANENT_SUSPENSION_THRESHOLD = LocalDateTime.of(2100, 1, 1, 0, 0);
}
