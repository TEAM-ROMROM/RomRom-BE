package com.romrom.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse.AdminTrafficTimeseries;
import com.romrom.common.constant.ActivityAggregation;
import com.romrom.common.constant.ActivityBucketUnit;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.OnlinePresenceService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 접속·방문자 시계열 서비스 단위 테스트: 기본 기간·버킷 수, 빈 버킷 0 채움, 기본 계산 방식, 요청 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminTrafficAnalyticsServiceTest {

  @Mock private AdminTrafficAnalyticsQuery adminTrafficAnalyticsQuery;
  @Mock private OnlinePresenceService onlinePresenceService;

  @InjectMocks
  private AdminTrafficAnalyticsService adminTrafficAnalyticsService;

  private static AdminRequest createTimeseriesRequest(ActivityEventType eventType, ActivityAggregation aggregation,
      ActivityBucketUnit bucketUnit, String startDate, String endDate) {
    AdminRequest timeseriesRequest = new AdminRequest();
    timeseriesRequest.setActivityEventType(eventType);
    timeseriesRequest.setActivityAggregation(aggregation);
    timeseriesRequest.setActivityBucketUnit(bucketUnit);
    timeseriesRequest.setStartDate(startDate);
    timeseriesRequest.setEndDate(endDate);
    return timeseriesRequest;
  }

  @Test
  @DisplayName("기간 미입력 HOUR는 최근 24시간 24칸, DAY는 최근 30일 30칸")
  void 기본_기간_버킷_수() {
    Assertions.assertEquals(AdminTrafficAnalyticsService.DEFAULT_HOUR_RANGE_HOURS,
        adminTrafficAnalyticsService.resolveKstBucketStarts(ActivityBucketUnit.HOUR, null, null).size());
    Assertions.assertEquals(AdminTrafficAnalyticsService.DEFAULT_DAY_RANGE_DAYS,
        adminTrafficAnalyticsService.resolveKstBucketStarts(ActivityBucketUnit.DAY, null, null).size());
  }

  @Test
  @DisplayName("요청 기간 HOUR는 시작일 00시~종료일 23시 (2일 = 48칸)")
  void 요청_기간_시간_버킷() {
    List<LocalDateTime> kstBucketStarts =
        adminTrafficAnalyticsService.resolveKstBucketStarts(ActivityBucketUnit.HOUR, "2026-11-01", "2026-11-02");
    Assertions.assertEquals(48, kstBucketStarts.size());
    Assertions.assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), kstBucketStarts.get(0));
    Assertions.assertEquals(LocalDateTime.of(2026, 11, 2, 23, 0), kstBucketStarts.get(47));
  }

  @Test
  @DisplayName("집계 결과에 없는 버킷은 0으로 채우고 라벨 순서는 오래된 순")
  void 빈_버킷_0_채움() {
    when(adminTrafficAnalyticsQuery.aggregateByBucket(eq(ActivityEventType.API_CALL), eq(ActivityAggregation.DISTINCT_MEMBERS),
        eq(ActivityBucketUnit.DAY), any(), any()))
        .thenReturn(Map.of("2026-11-02", 7L));

    AdminTrafficTimeseries trafficTimeseries = adminTrafficAnalyticsService.getTrafficTimeseries(createTimeseriesRequest(
        ActivityEventType.API_CALL, ActivityAggregation.DISTINCT_MEMBERS, ActivityBucketUnit.DAY, "2026-11-01", "2026-11-03"))
        .getTrafficTimeseries();

    Assertions.assertEquals(List.of("2026-11-01", "2026-11-02", "2026-11-03"), trafficTimeseries.getBucketLabels());
    Assertions.assertEquals(List.of(0L, 7L, 0L), trafficTimeseries.getBucketValues());
  }

  @Test
  @DisplayName("계산 방식 미입력이면 이벤트 종류 기본값 (CONCURRENT_USERS > MAX_METRIC_VALUE), 버킷 미입력이면 DAY")
  void 기본_계산_방식과_버킷() {
    when(adminTrafficAnalyticsQuery.aggregateByBucket(any(), any(), any(), any(), any())).thenReturn(Map.of());

    AdminTrafficTimeseries trafficTimeseries = adminTrafficAnalyticsService.getTrafficTimeseries(
        createTimeseriesRequest(ActivityEventType.CONCURRENT_USERS, null, null, null, null)).getTrafficTimeseries();

    Assertions.assertEquals(ActivityAggregation.MAX_METRIC_VALUE, trafficTimeseries.getActivityAggregation());
    Assertions.assertEquals(ActivityBucketUnit.DAY, trafficTimeseries.getActivityBucketUnit());
    ArgumentCaptor<ActivityAggregation> aggregationCaptor = ArgumentCaptor.forClass(ActivityAggregation.class);
    verify(adminTrafficAnalyticsQuery).aggregateByBucket(eq(ActivityEventType.CONCURRENT_USERS), aggregationCaptor.capture(),
        eq(ActivityBucketUnit.DAY), any(), any());
    Assertions.assertEquals(ActivityAggregation.MAX_METRIC_VALUE, aggregationCaptor.getValue());
  }

  @Test
  @DisplayName("이벤트 종류 누락·역순 기간·최대 기간 초과·날짜 형식 오류는 INVALID_REQUEST")
  void 잘못된_요청_400() {
    assertInvalidRequest(createTimeseriesRequest(null, null, ActivityBucketUnit.DAY, null, null));
    assertInvalidRequest(createTimeseriesRequest(ActivityEventType.API_CALL, null, ActivityBucketUnit.DAY, "2026-11-03", "2026-11-01"));
    assertInvalidRequest(createTimeseriesRequest(ActivityEventType.API_CALL, null, ActivityBucketUnit.HOUR, "2026-11-01", "2026-11-08"));
    assertInvalidRequest(createTimeseriesRequest(ActivityEventType.API_CALL, null, ActivityBucketUnit.DAY, "2026/11/01", "2026-11-02"));
  }

  private void assertInvalidRequest(AdminRequest invalidRequest) {
    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> adminTrafficAnalyticsService.getTrafficTimeseries(invalidRequest));
    Assertions.assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
  }
}
