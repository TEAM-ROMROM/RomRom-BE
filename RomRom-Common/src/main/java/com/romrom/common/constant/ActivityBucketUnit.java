package com.romrom.common.constant;

import java.time.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 시계열 버킷 단위 2개: 시간(최대 7일), 일(최대 92일) / Mongo $dateToString 형식과 짝
 */
@AllArgsConstructor
@Getter
public enum ActivityBucketUnit {
  HOUR("%Y-%m-%d %H:00", Duration.ofHours(1), 7),
  DAY("%Y-%m-%d", Duration.ofDays(1), 92);

  // KST 기준 버킷 라벨 형식 (예: 2026-11-01 13:00, 2026-11-01)
  private final String mongoDateFormat;
  private final Duration bucketDuration;
  // 한 번에 조회 가능한 최대 기간 (일)
  private final int maximumRangeDays;
}
