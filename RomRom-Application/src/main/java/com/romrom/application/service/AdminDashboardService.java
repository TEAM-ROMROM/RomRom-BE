package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository.TradeStatusCountProjection;
import com.romrom.item.repository.postgres.TradeReviewRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 대시보드 통계 조립 서비스
 * - 거래 상태별 카운트(데이터 주도), 신규 후기 카운트, 최근 교환완료 거래
 * - 기간 필터(startDate/endDate) 지원. 둘 다 없으면 전체 누적
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

  private static final DateTimeFormatter DASHBOARD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final TradeReviewRepository tradeReviewRepository;

  /**
   * 거래 상태별 카운트 + 신규 후기 카운트 (기간 필터 적용)
   * - 모든 TradeStatus 키를 0으로 초기화한 뒤 집계 결과를 덮어써, 건수 0인 상태도 카드로 노출되게 한다
   */
  @Transactional(readOnly = true)
  public AdminResponse.AdminDashboardStats getTradeStatusStats(AdminRequest request) {
    LocalDateTime startDateTime = parseStartDate(request.getStartDate());
    LocalDateTime endDateTime = parseEndDate(request.getEndDate());

    Map<TradeStatus, Long> tradeStatusCounts = new EnumMap<>(TradeStatus.class);
    for (TradeStatus tradeStatus : TradeStatus.values()) {
      tradeStatusCounts.put(tradeStatus, 0L);
    }
    for (TradeStatusCountProjection countProjection :
        tradeRequestHistoryRepository.countGroupedByTradeStatus(startDateTime, endDateTime)) {
      tradeStatusCounts.put(countProjection.getTradeStatus(), countProjection.getCount());
    }

    long newReviewCount = tradeReviewRepository.countByCreatedDateBetweenNullable(startDateTime, endDateTime);

    log.info("대시보드 거래 상태별 통계 조회: 기간={}~{}, 후기={}건",
        request.getStartDate(), request.getEndDate(), newReviewCount);

    return AdminResponse.AdminDashboardStats.builder()
        .tradeStatusCounts(tradeStatusCounts)
        .newReviewCount(newReviewCount)
        .build();
  }

  /**
   * 최근 교환완료(TRADED) 거래 N건
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentTrades(int recentTradeLimit) {
    List<TradeRequestHistory> recentTrades =
        tradeRequestHistoryRepository.findRecentTradedForAdmin(PageRequest.of(0, recentTradeLimit));

    log.info("대시보드 최근 교환완료 거래 조회: {}건", recentTrades.size());

    return AdminResponse.builder()
        .recentTrades(recentTrades)
        .build();
  }

  /**
   * 기간 시작일 파싱: yyyy-MM-dd → 해당 일자 00:00:00
   */
  private LocalDateTime parseStartDate(String startDateString) {
    LocalDate localDate = parseLocalDate(startDateString);
    return localDate == null ? null : localDate.atStartOfDay();
  }

  /**
   * 기간 종료일 파싱: yyyy-MM-dd → 해당 일자 23:59:59.999999999 (inclusive)
   */
  private LocalDateTime parseEndDate(String endDateString) {
    LocalDate localDate = parseLocalDate(endDateString);
    return localDate == null ? null : localDate.atTime(java.time.LocalTime.MAX);
  }

  /**
   * yyyy-MM-dd 파싱. 미입력은 null(전체 기간), 형식 오류는 INVALID_REQUEST 로 끊는다.
   * - 잘못된 날짜를 null 로 삼키면 "기간 오류"가 "전체 누적 조회"로 둔갑해 관리자에게 정상처럼 보임
   */
  private LocalDate parseLocalDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(dateString.trim(), DASHBOARD_DATE_FORMAT);
    } catch (Exception e) {
      log.warn("대시보드 날짜 파싱 실패: {}", dateString);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }
}
