package com.romrom.application.util;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 대시보드 분석 기간: 오늘 포함 N일(현재) + 바로 앞 같은 길이(직전) (#827)
 * 예: 7일, 오늘 10-29 > 현재 10-23~10-29, 직전 10-16~10-22
 */
public record AnalyticsPeriod(int periodDays, LocalDate currentStartDate, LocalDate endDate, LocalDate previousStartDate) {

  // 기간 미입력 기본값 (일)
  public static final int DEFAULT_PERIOD_DAYS = 30;
  // 허용 기간 (일): 직전 기간까지 2배 조회하므로 상한 고정
  public static final int MIN_PERIOD_DAYS = 1;
  public static final int MAX_PERIOD_DAYS = 180;

  /**
   * 요청 기간(일, null이면 기본 30) + 기준일 > 분석 기간, 범위 밖이면 INVALID_REQUEST
   */
  public static AnalyticsPeriod of(Integer requestedPeriodDays, LocalDate today) {
    int periodDays = requestedPeriodDays != null ? requestedPeriodDays : DEFAULT_PERIOD_DAYS;
    if (periodDays < MIN_PERIOD_DAYS || periodDays > MAX_PERIOD_DAYS) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    LocalDate currentStartDate = today.minusDays(periodDays - 1L);
    return new AnalyticsPeriod(periodDays, currentStartDate, today, currentStartDate.minusDays(periodDays));
  }

  /**
   * 직전 기간 마지막 날 (= 현재 시작 전날)
   */
  public LocalDate previousEndDate() {
    return currentStartDate.minusDays(1);
  }

  /**
   * 조회 시작 시각: 직전 기간 첫날 00:00 (현재·직전을 쿼리 1번으로 받기 위함)
   */
  public LocalDateTime queryStartDateTime() {
    return previousStartDate.atStartOfDay();
  }

  public LocalDateTime currentStartDateTime() {
    return currentStartDate.atStartOfDay();
  }

  /**
   * 조회 종료 시각: 오늘 23:59:59.999999999 (오늘 포함)
   */
  public LocalDateTime endDateTime() {
    return endDate.atTime(LocalTime.MAX);
  }

  /**
   * 현재 기간 날짜 목록 (yyyy-MM-dd, 오래된 순)
   */
  public List<String> currentDates() {
    return datesBetween(currentStartDate, endDate);
  }

  public List<String> previousDates() {
    return datesBetween(previousStartDate, previousEndDate());
  }

  private static List<String> datesBetween(LocalDate firstDate, LocalDate lastDate) {
    List<String> dates = new ArrayList<>();
    for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
      dates.add(date.toString());
    }
    return dates;
  }
}
