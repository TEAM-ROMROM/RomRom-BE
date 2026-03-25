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

  /**
   * 영구정지 내부 기준값 (현재 실제 저장 시에는 사용하지 않음).
   *
   * <p>실제 영구정지는 100년 후 날짜(suspendedUntil >= 2100-01-01)로 저장하며,
   * 프론트엔드는 2100년 이상 여부로 "영구정지" 라벨을 표시한다.
   * 이 상수는 일시/영구 정지 구분 쿼리에서 임계값({@code permanentThreshold})으로만 사용된다.
   * 의도된 설계: "영구정지 = 100년 정지"이므로 백엔드 상수(9999년)와 실제 저장값(2100년)이 다른 것은 정상.</p>
   */
  public static final LocalDateTime PERMANENT_SUSPENSION_UNTIL = LocalDateTime.of(9999, 12, 31, 23, 59, 59);
}
