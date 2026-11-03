package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.AdminResponse.AdminTrafficRealtime;
import com.romrom.application.dto.AdminResponse.AdminTrafficTimeseries;
import com.romrom.common.constant.ActivityAggregation;
import com.romrom.common.constant.ActivityBucketUnit;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.OnlinePresenceService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자 접속·방문자 분석 (#772): 실시간 지표, 활동 이벤트 범용 시계열 (빈 버킷 0 채움)
 * 기간: startDate·endDate(yyyy-MM-dd, KST, 종료일 포함), 미입력 시 HOUR 최근 24시간 · DAY 최근 30일
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTrafficAnalyticsService {

  static final ZoneId KST_ZONE_ID = ZoneId.of(AdminTrafficAnalyticsQuery.KST_TIMEZONE);
  // 기간 미입력 기본값
  static final int DEFAULT_HOUR_RANGE_HOURS = 24;
  static final int DEFAULT_DAY_RANGE_DAYS = 30;
  private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter HOUR_BUCKET_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
  private static final DateTimeFormatter DAY_BUCKET_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final AdminTrafficAnalyticsQuery adminTrafficAnalyticsQuery;
  private final OnlinePresenceService onlinePresenceService;

  /**
   * 실시간: 현재 온라인(Redis) + 오늘 API 호출 수·고유 회원 수(이벤트 스트림)
   */
  public AdminResponse getTrafficRealtime() {
    LocalDateTime measuredAt = LocalDateTime.now();
    LocalDateTime todayStart = toSystemDateTime(LocalDate.now(KST_ZONE_ID).atStartOfDay(KST_ZONE_ID).toLocalDateTime());
    return AdminResponse.builder()
        .trafficRealtime(AdminTrafficRealtime.builder()
            .onlineMemberCount(onlinePresenceService.countOnlineMembers(System.currentTimeMillis()))
            .todayApiCallCount(adminTrafficAnalyticsQuery.countEvents(ActivityEventType.API_CALL, todayStart, measuredAt))
            .todayActiveMemberCount(
                adminTrafficAnalyticsQuery.countDistinctMembers(ActivityEventType.API_CALL, todayStart, measuredAt))
            .measuredAt(measuredAt)
            .build())
        .build();
  }

  /**
   * 시계열: 요청 검증 > 기간 확정(KST 버킷 경계) > 집계 > 빈 버킷 0 채움
   */
  public AdminResponse getTrafficTimeseries(AdminRequest request) {
    ActivityEventType eventType = request.getActivityEventType();
    if (eventType == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    ActivityBucketUnit bucketUnit = request.getActivityBucketUnit() != null ? request.getActivityBucketUnit() : ActivityBucketUnit.DAY;
    ActivityAggregation aggregation = request.getActivityAggregation() != null
        ? request.getActivityAggregation() : eventType.getDefaultAggregation();

    List<LocalDateTime> kstBucketStarts = resolveKstBucketStarts(bucketUnit, request.getStartDate(), request.getEndDate());
    LocalDateTime kstRangeStart = kstBucketStarts.get(0);
    LocalDateTime kstRangeEnd = kstBucketStarts.get(kstBucketStarts.size() - 1).plus(bucketUnit.getBucketDuration());

    Map<String, Long> valueByBucketLabel = adminTrafficAnalyticsQuery.aggregateByBucket(
        eventType, aggregation, bucketUnit, toSystemDateTime(kstRangeStart), toSystemDateTime(kstRangeEnd));

    List<String> bucketLabels = new ArrayList<>(kstBucketStarts.size());
    List<Long> bucketValues = new ArrayList<>(kstBucketStarts.size());
    DateTimeFormatter bucketLabelFormatter = bucketUnit == ActivityBucketUnit.HOUR ? HOUR_BUCKET_LABEL_FORMATTER : DAY_BUCKET_LABEL_FORMATTER;
    for (LocalDateTime kstBucketStart : kstBucketStarts) {
      String bucketLabel = kstBucketStart.format(bucketLabelFormatter);
      bucketLabels.add(bucketLabel);
      bucketValues.add(valueByBucketLabel.getOrDefault(bucketLabel, 0L));
    }

    return AdminResponse.builder()
        .trafficTimeseries(AdminTrafficTimeseries.builder()
            .activityEventType(eventType)
            .activityAggregation(aggregation)
            .activityBucketUnit(bucketUnit)
            .bucketLabels(bucketLabels)
            .bucketValues(bucketValues)
            .build())
        .build();
  }

  /**
   * 버킷 단위 + 요청 기간 > KST 버킷 시작 시각 목록 (오래된 순, 단위별 최대 기간 초과·역순·형식 오류는 400)
   */
  List<LocalDateTime> resolveKstBucketStarts(ActivityBucketUnit bucketUnit, String startDateText, String endDateText) {
    LocalDateTime kstNow = LocalDateTime.now(KST_ZONE_ID);
    LocalDateTime kstRangeStart;
    LocalDateTime kstLastBucketStart;
    boolean hasRequestedPeriod = hasText(startDateText) && hasText(endDateText);

    if (hasRequestedPeriod) {
      LocalDate startDate = parseRequestDate(startDateText);
      LocalDate endDate = parseRequestDate(endDateText);
      if (endDate.isBefore(startDate) || ChronoUnit.DAYS.between(startDate, endDate) + 1 > bucketUnit.getMaximumRangeDays()) {
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
      kstRangeStart = startDate.atStartOfDay();
      kstLastBucketStart = bucketUnit == ActivityBucketUnit.HOUR ? endDate.atTime(23, 0) : endDate.atStartOfDay();
    } else if (bucketUnit == ActivityBucketUnit.HOUR) {
      kstLastBucketStart = kstNow.truncatedTo(ChronoUnit.HOURS);
      kstRangeStart = kstLastBucketStart.minusHours(DEFAULT_HOUR_RANGE_HOURS - 1);
    } else {
      kstLastBucketStart = kstNow.toLocalDate().atStartOfDay();
      kstRangeStart = kstLastBucketStart.minusDays(DEFAULT_DAY_RANGE_DAYS - 1);
    }

    List<LocalDateTime> kstBucketStarts = new ArrayList<>();
    for (LocalDateTime kstBucketStart = kstRangeStart; !kstBucketStart.isAfter(kstLastBucketStart);
        kstBucketStart = kstBucketStart.plus(bucketUnit.getBucketDuration())) {
      kstBucketStarts.add(kstBucketStart);
    }
    return kstBucketStarts;
  }

  // KST 벽시계 시각 > 시스템 시간대 LocalDateTime (Mongo Date 변환이 시스템 시간대 기준)
  private static LocalDateTime toSystemDateTime(LocalDateTime kstDateTime) {
    return kstDateTime.atZone(KST_ZONE_ID).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
  }

  private static LocalDate parseRequestDate(String requestDateText) {
    try {
      return LocalDate.parse(requestDateText.trim(), REQUEST_DATE_FORMATTER);
    } catch (DateTimeParseException dateTimeParseException) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private static boolean hasText(String text) {
    return text != null && !text.isBlank();
  }
}
