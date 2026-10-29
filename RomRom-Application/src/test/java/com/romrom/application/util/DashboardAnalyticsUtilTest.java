package com.romrom.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.romrom.application.dto.AdminResponse.AdminMetricSummary;
import com.romrom.common.dto.DailyCountProjection;
import com.romrom.common.exception.CustomException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대시보드 분석 기간·계열 계산 검증 (#827)
 */
class DashboardAnalyticsUtilTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);

  @Test
  @DisplayName("7일: 현재 10-23~10-29, 직전 10-16~10-22, 조회는 직전 시작부터 오늘 끝까지")
  void 기간은_오늘_포함_N일과_직전_N일이다() {
    AnalyticsPeriod analyticsPeriod = AnalyticsPeriod.of(7, TODAY);

    assertEquals(LocalDate.of(2026, 10, 23), analyticsPeriod.currentStartDate());
    assertEquals(LocalDate.of(2026, 10, 16), analyticsPeriod.previousStartDate());
    assertEquals(LocalDate.of(2026, 10, 22), analyticsPeriod.previousEndDate());
    assertEquals(7, analyticsPeriod.currentDates().size());
    assertEquals("2026-10-29", analyticsPeriod.currentDates().get(6));
    assertEquals(7, analyticsPeriod.previousDates().size());
    assertEquals(LocalDate.of(2026, 10, 16).atStartOfDay(), analyticsPeriod.queryStartDateTime());
  }

  @Test
  @DisplayName("기간 미입력 30일, 0·181일은 INVALID_REQUEST")
  void 기간_기본값과_범위를_검사한다() {
    assertEquals(30, AnalyticsPeriod.of(null, TODAY).periodDays());
    assertThrows(CustomException.class, () -> AnalyticsPeriod.of(0, TODAY));
    assertThrows(CustomException.class, () -> AnalyticsPeriod.of(181, TODAY));
  }

  @Test
  @DisplayName("빈 날짜 0 채움, 같은 날짜 합산, 현재·직전 합과 증감률")
  void 날짜별_계열과_비교_지표를_만든다() {
    AnalyticsPeriod analyticsPeriod = AnalyticsPeriod.of(2, TODAY);
    Map<String, Long> countByDay = DailySeriesUtil.toCountByDay(List.of(
        dailyCount("2026-10-27", 2L), dailyCount("2026-10-29", 3L), dailyCount("2026-10-29", 1L)));

    assertEquals(List.of(0L, 4L), DailySeriesUtil.toSeries(analyticsPeriod.currentDates(), countByDay));
    AdminMetricSummary metricSummary = DailySeriesUtil.toMetricSummary(analyticsPeriod, countByDay);
    assertEquals(4L, metricSummary.getCurrentValue());
    assertEquals(2L, metricSummary.getPreviousValue());
    assertEquals(100.0, metricSummary.getChangeRate());
  }

  @Test
  @DisplayName("증감률: 직전 0이면 null, 소수 1자리 반올림, 감소는 음수")
  void 증감률을_계산한다() {
    assertNull(DailySeriesUtil.toChangeRate(5, 0));
    assertEquals(33.3, DailySeriesUtil.toChangeRate(4, 3));
    assertEquals(-50.0, DailySeriesUtil.toChangeRate(1, 2));
  }

  private DailyCountProjection dailyCount(String day, Long count) {
    return new DailyCountProjection() {
      @Override
      public String getDay() {
        return day;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }
}
