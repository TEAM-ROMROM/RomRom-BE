package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 시계열 버킷 값 계산 방식 3개: 이벤트 수, 고유 회원 수, 스냅샷 값 최대치
 */
@AllArgsConstructor
@Getter
public enum ActivityAggregation {
  EVENT_COUNT("이벤트 수"),
  DISTINCT_MEMBERS("고유 회원 수"),
  MAX_METRIC_VALUE("최대값");

  private final String description;
}
