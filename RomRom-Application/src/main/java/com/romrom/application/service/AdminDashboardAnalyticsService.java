package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.AdminResponse.AdminDashboardAnalytics;
import com.romrom.application.dto.AdminResponse.AdminLabeledCount;
import com.romrom.application.dto.AdminResponse.AdminMetricSummary;
import com.romrom.application.util.AnalyticsPeriod;
import com.romrom.application.util.DailySeriesUtil;
import com.romrom.common.constant.AiUsageType;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.dto.GroupCountProjection;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import com.romrom.item.repository.postgres.ViewHistoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 운영 분석 (#827): 기간 계산 > 날짜별 집계(현재+직전 1회 조회) > 계열·비교 지표 > 분포
 * 지표 정의: docs/superpowers/specs/2026-10-29-admin-dashboard-ui-design.md
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardAnalyticsService {

  private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
  // 지역 분포 상위 개수
  private static final int TOP_REGION_LIMIT = 10;
  // 가입 경로·기기 표시 이름 (enum에 설명 필드 없음)
  private static final Map<String, String> SOCIAL_PLATFORM_LABELS = Map.of(
      "KAKAO", "카카오", "GOOGLE", "구글", "APPLE", "애플", "NORMAL", "일반", "UNKNOWN", "미지정");
  private static final Map<String, String> DEVICE_TYPE_LABELS = Map.of("ANDROID", "Android", "IOS", "iOS", "OTHER", "기타");

  private final MemberRepository memberRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final ItemRepository itemRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final TradeReviewRepository tradeReviewRepository;
  private final ViewHistoryRepository viewHistoryRepository;
  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;
  private final AdminActivityAnalyticsQuery adminActivityAnalyticsQuery;
  private final AdminReportService adminReportService;
  private final AdminDashboardService adminDashboardService;

  @Transactional(readOnly = true)
  public AdminResponse getDashboardAnalytics(AdminRequest request) {
    AnalyticsPeriod analyticsPeriod = AnalyticsPeriod.of(request.getAnalyticsPeriodDays(), LocalDate.now(KST_ZONE));
    Map<String, Map<String, Long>> countByDayByMetric = loadDailyCounts(analyticsPeriod);

    AdminDashboardAnalytics dashboardAnalytics = AdminDashboardAnalytics.builder()
        .periodDays(analyticsPeriod.periodDays())
        .startDate(analyticsPeriod.currentStartDate().toString())
        .endDate(analyticsPeriod.endDate().toString())
        .dates(analyticsPeriod.currentDates())
        .dailySeries(toDailySeries(analyticsPeriod, countByDayByMetric))
        .metrics(toMetrics(analyticsPeriod, countByDayByMetric))
        .build();
    fillCurrentState(dashboardAnalytics);
    fillDistributions(dashboardAnalytics, analyticsPeriod);

    log.info("[admin dashboard] analytics : periodDays={}, startDate={}", analyticsPeriod.periodDays(), analyticsPeriod.currentStartDate());
    return AdminResponse.builder().dashboardAnalytics(dashboardAnalytics).build();
  }

  /**
   * 날짜별 집계 (직전 기간 시작 ~ 오늘): 지표 키 > {날짜: 건수}
   */
  private Map<String, Map<String, Long>> loadDailyCounts(AnalyticsPeriod analyticsPeriod) {
    LocalDateTime queryStartDateTime = analyticsPeriod.queryStartDateTime();
    LocalDateTime endDateTime = analyticsPeriod.endDateTime();
    Map<String, Map<String, Long>> countByDayByMetric = new LinkedHashMap<>();
    countByDayByMetric.put("newMembers", DailySeriesUtil.toCountByDay(
        memberRepository.countDailyNewMembers(queryStartDateTime, endDateTime)));
    countByDayByMetric.put("newItems", DailySeriesUtil.toCountByDay(
        itemRepository.countDailyNewItems(queryStartDateTime, endDateTime)));
    countByDayByMetric.put("tradeRequests", DailySeriesUtil.toCountByDay(
        tradeRequestHistoryRepository.countDailyTradeRequests(queryStartDateTime, endDateTime)));
    countByDayByMetric.put("completedTrades", DailySeriesUtil.toCountByDay(
        tradeRequestHistoryRepository.countDailyByTradeStatusUpdated(TradeStatus.TRADED.ordinal(), queryStartDateTime, endDateTime)));
    countByDayByMetric.put("newReviews", DailySeriesUtil.toCountByDay(
        tradeReviewRepository.countDailyNewReviews(queryStartDateTime, endDateTime)));
    countByDayByMetric.put("itemViews", DailySeriesUtil.toCountByDay(
        viewHistoryRepository.countDailyViews(analyticsPeriod.previousStartDate(), analyticsPeriod.endDate())));
    countByDayByMetric.put("loginMembers", adminActivityAnalyticsQuery.countDailyLoginMembers(queryStartDateTime, endDateTime));
    countByDayByMetric.put("chatMessages", adminActivityAnalyticsQuery.countDailyChatMessages(queryStartDateTime, endDateTime));
    countByDayByMetric.put("aiCalls", adminActivityAnalyticsQuery.countDailyAiCalls(queryStartDateTime, endDateTime, false));
    countByDayByMetric.put("aiFailures", adminActivityAnalyticsQuery.countDailyAiCalls(queryStartDateTime, endDateTime, true));
    return countByDayByMetric;
  }

  private Map<String, List<Long>> toDailySeries(AnalyticsPeriod analyticsPeriod, Map<String, Map<String, Long>> countByDayByMetric) {
    List<String> currentDates = analyticsPeriod.currentDates();
    Map<String, List<Long>> dailySeries = new LinkedHashMap<>();
    countByDayByMetric.forEach((metricKey, countByDay) ->
        dailySeries.put(metricKey, DailySeriesUtil.toSeries(currentDates, countByDay)));
    return dailySeries;
  }

  /**
   * 기간 비교 지표 (AI 실패는 aiCalls 비교에 포함되므로 제외)
   */
  private Map<String, AdminMetricSummary> toMetrics(AnalyticsPeriod analyticsPeriod, Map<String, Map<String, Long>> countByDayByMetric) {
    Map<String, AdminMetricSummary> metrics = new LinkedHashMap<>();
    countByDayByMetric.forEach((metricKey, countByDay) -> {
      if (!"aiFailures".equals(metricKey)) {
        metrics.put(metricKey, DailySeriesUtil.toMetricSummary(analyticsPeriod, countByDay));
      }
    });
    return metrics;
  }

  /**
   * 현재 상태 지표: 활성 회원·DAU·WAU·MAU·활성 물품·대기 신고·완료요청 거래 (기간 무관)
   */
  private void fillCurrentState(AdminDashboardAnalytics dashboardAnalytics) {
    LocalDateTime now = LocalDateTime.now(KST_ZONE);
    dashboardAnalytics.setTotalMembers(memberRepository.countActiveMembers());
    dashboardAnalytics.setDailyActiveMembers(memberRepository.countActiveMembersSince(now.minusDays(1)));
    dashboardAnalytics.setWeeklyActiveMembers(memberRepository.countActiveMembersSince(now.minusDays(7)));
    dashboardAnalytics.setMonthlyActiveMembers(memberRepository.countActiveMembersSince(now.minusDays(30)));
    dashboardAnalytics.setActiveItems(itemRepository.countByIsDeletedFalse());
    dashboardAnalytics.setPendingReports(adminReportService.countPendingReports());
    dashboardAnalytics.setCompleteRequestedTrades(adminDashboardService.countCompleteRequestedTrades());
  }

  /**
   * 분포: 거래 상태·신고 상태·후기·AI·기기(기간), 카테고리·지역·가입 경로(현재)
   */
  private void fillDistributions(AdminDashboardAnalytics dashboardAnalytics, AnalyticsPeriod analyticsPeriod) {
    LocalDateTime currentStartDateTime = analyticsPeriod.currentStartDateTime();
    LocalDateTime endDateTime = analyticsPeriod.endDateTime();

    Map<String, Long> tradeStatusCountByKey = new HashMap<>();
    tradeRequestHistoryRepository.countGroupedByTradeStatus(currentStartDateTime, endDateTime)
        .forEach(countProjection -> tradeStatusCountByKey.put(countProjection.getTradeStatus().name(), countProjection.getCount()));
    dashboardAnalytics.setTradeStatusCounts(toLabeledCounts(tradeStatusCountByKey, key -> TradeStatus.valueOf(key).getDescription()));

    Map<String, Long> reportStatusCountByKey = toCountByKey(itemReportRepository.countGroupedByStatusBetween(currentStartDateTime, endDateTime));
    toCountByKey(memberReportRepository.countGroupedByStatusBetween(currentStartDateTime, endDateTime))
        .forEach((statusKey, statusCount) -> reportStatusCountByKey.merge(statusKey, statusCount, Long::sum));
    dashboardAnalytics.setReportStatusCounts(toLabeledCounts(reportStatusCountByKey, key -> ReportStatus.valueOf(key).getDescription()));

    dashboardAnalytics.setReviewRatingCounts(toLabeledCounts(
        toCountByKey(tradeReviewRepository.countGroupedByRating(currentStartDateTime, endDateTime)),
        key -> TradeReviewRating.valueOf(key).getDescription()));
    dashboardAnalytics.setAiUsageTypeCounts(toLabeledCounts(
        adminActivityAnalyticsQuery.countAiCallsByType(currentStartDateTime, endDateTime),
        key -> AiUsageType.valueOf(key).getDescription()));
    dashboardAnalytics.setDeviceTypeCounts(toLabeledCounts(
        adminActivityAnalyticsQuery.countLoginsByDeviceType(currentStartDateTime, endDateTime),
        key -> DEVICE_TYPE_LABELS.getOrDefault(key, key)));
    dashboardAnalytics.setLoginHourlyCounts(adminActivityAnalyticsQuery.countHourlyLogins(currentStartDateTime, endDateTime));

    dashboardAnalytics.setItemCategoryCounts(toLabeledCounts(
        toCountByKey(itemRepository.countActiveItemsGroupedByCategoryCode()),
        key -> ItemCategory.fromCode(Integer.parseInt(key)).getDescription()));
    dashboardAnalytics.setRegionCounts(toLabeledCounts(
        toCountByKey(memberLocationRepository.countTopRegions(TOP_REGION_LIMIT)), Function.identity()));
    dashboardAnalytics.setSocialPlatformCounts(toLabeledCounts(
        toCountByKey(memberRepository.countGroupedBySocialPlatform()),
        key -> SOCIAL_PLATFORM_LABELS.getOrDefault(key, key)));
  }

  private Map<String, Long> toCountByKey(List<GroupCountProjection> groupCounts) {
    Map<String, Long> countByKey = new HashMap<>();
    groupCounts.forEach(groupCount -> {
      if (groupCount.getGroupKey() != null && groupCount.getCount() != null) {
        countByKey.merge(groupCount.getGroupKey(), groupCount.getCount(), Long::sum);
      }
    });
    return countByKey;
  }

  /**
   * {키: 건수} > 표시 이름 붙인 목록 (건수 많은 순, 이름 변환 실패 시 키 그대로)
   */
  static List<AdminLabeledCount> toLabeledCounts(Map<String, Long> countByKey, Function<String, String> toLabel) {
    return countByKey.entrySet().stream()
        .map(countEntry -> AdminLabeledCount.builder()
            .key(countEntry.getKey())
            .label(toSafeLabel(countEntry.getKey(), toLabel))
            .count(countEntry.getValue())
            .build())
        .sorted(Comparator.comparing(AdminLabeledCount::getCount).reversed().thenComparing(AdminLabeledCount::getKey))
        .toList();
  }

  private static String toSafeLabel(String key, Function<String, String> toLabel) {
    try {
      String label = toLabel.apply(key);
      return label != null ? label : key;
    } catch (RuntimeException labelConversionException) {
      return key;
    }
  }
}
