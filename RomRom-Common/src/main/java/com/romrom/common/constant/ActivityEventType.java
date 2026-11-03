package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 활동 이벤트 종류: 새 지표 = 값 1개 추가 + 수집 지점에서 ActivityEventRecorder 호출 (컬렉션·마이그레이션 변경 없음)
 */
@AllArgsConstructor
@Getter
public enum ActivityEventType {
  // 앱 API 호출 1건 (관리자 API 제외), properties: httpMethod, pathPattern, statusCode
  API_CALL("API 호출", ActivityEventCategory.TRAFFIC, ActivityAggregation.EVENT_COUNT),
  // 동시접속자 스냅샷 (5분 주기), metricValue = 그 시점 온라인 회원 수
  CONCURRENT_USERS("동시접속자", ActivityEventCategory.SYSTEM, ActivityAggregation.MAX_METRIC_VALUE);

  private final String description;
  private final ActivityEventCategory eventCategory;
  // 시계열 조회 시 aggregation 미지정이면 쓰는 기본 계산 방식
  private final ActivityAggregation defaultAggregation;
}
