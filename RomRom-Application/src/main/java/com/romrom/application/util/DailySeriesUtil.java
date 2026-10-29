package com.romrom.application.util;

import com.romrom.application.dto.AdminResponse.AdminMetricSummary;
import com.romrom.common.dto.DailyCountProjection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 날짜별 집계 변환 (#827): 집계 결과 > 날짜 맵 > 0 채운 계열 > 기간 합 > 전기간 대비
 */
public final class DailySeriesUtil {

  private DailySeriesUtil() {
  }

  /**
   * 집계 결과 > 날짜(yyyy-MM-dd)별 건수 맵 (같은 날짜 중복 시 합산)
   */
  public static Map<String, Long> toCountByDay(List<DailyCountProjection> dailyCounts) {
    Map<String, Long> countByDay = new HashMap<>();
    for (DailyCountProjection dailyCount : dailyCounts) {
      if (dailyCount.getDay() != null && dailyCount.getCount() != null) {
        countByDay.merge(dailyCount.getDay(), dailyCount.getCount(), Long::sum);
      }
    }
    return countByDay;
  }

  /**
   * 날짜 목록 순서대로 건수 (없는 날짜 0)
   */
  public static List<Long> toSeries(List<String> dates, Map<String, Long> countByDay) {
    return dates.stream().map(date -> countByDay.getOrDefault(date, 0L)).toList();
  }

  /**
   * 날짜 목록 구간 합
   */
  public static long sumOf(List<String> dates, Map<String, Long> countByDay) {
    return dates.stream().mapToLong(date -> countByDay.getOrDefault(date, 0L)).sum();
  }

  /**
   * 현재·직전 값 > 비교 지표 (직전 0이면 증감률 null)
   */
  public static AdminMetricSummary toMetricSummary(long currentValue, long previousValue) {
    return AdminMetricSummary.builder()
        .currentValue(currentValue)
        .previousValue(previousValue)
        .changeRate(toChangeRate(currentValue, previousValue))
        .build();
  }

  /**
   * 증감률(%) 소수 1자리: (현재 - 직전) / 직전 × 100, 직전 0 > null
   */
  public static Double toChangeRate(long currentValue, long previousValue) {
    if (previousValue == 0) {
      return null;
    }
    double changeRatePercent = (currentValue - previousValue) * 100.0 / previousValue;
    return Math.round(changeRatePercent * 10) / 10.0;
  }

  /**
   * 기간 비교 지표 (날짜 맵 하나로 현재·직전 합)
   */
  public static AdminMetricSummary toMetricSummary(AnalyticsPeriod analyticsPeriod, Map<String, Long> countByDay) {
    return toMetricSummary(
        sumOf(analyticsPeriod.currentDates(), countByDay),
        sumOf(analyticsPeriod.previousDates(), countByDay));
  }
}
