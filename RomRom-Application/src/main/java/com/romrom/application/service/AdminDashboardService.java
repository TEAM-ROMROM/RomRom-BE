package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.util.AdminDateRangeParser;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.OnlinePresenceService;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
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


  private final ItemRepository itemRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final TradeReviewRepository tradeReviewRepository;
  private final OnlinePresenceService onlinePresenceService;
  private final ChatUserStateRepository chatUserStateRepository;

  /**
   * 거래 상태별 카운트 + 신규 후기 카운트 (기간 필터 적용)
   * - 모든 TradeStatus 키를 0으로 초기화한 뒤 집계 결과를 덮어써, 건수 0인 상태도 카드로 노출되게 한다
   */
  @Transactional(readOnly = true)
  public AdminResponse.AdminDashboardStats getTradeStatusStats(AdminRequest request) {
    LocalDateTime startDateTime = AdminDateRangeParser.parseStartOfDay(request.getStartDate());
    LocalDateTime endDateTime = AdminDateRangeParser.parseEndOfDay(request.getEndDate());

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
   * 동접자(온라인 사용자) 조회
   * - onlineMemberCount: 최근 5분 내 인증 API를 호출한 고유 회원 수 (Redis heartbeat 기반)
   * - chatOnlineMemberCount: 현재 채팅방 접속 중(leftAt == null)인 고유 회원 수 (MongoDB 집계)
   */
  public AdminResponse getOnlineStats() {
    long onlineMemberCount = onlinePresenceService.countOnlineMembers(System.currentTimeMillis());
    long chatOnlineMemberCount = chatUserStateRepository.countOnlineChatMembers();

    log.info("대시보드 동접자 조회: 앱 전체={}명, 채팅 온라인={}명", onlineMemberCount, chatOnlineMemberCount);

    return AdminResponse.builder()
        .onlineMemberCount(onlineMemberCount)
        .chatOnlineMemberCount(chatOnlineMemberCount)
        .build();
  }

  /**
   * 활성 아이템 수 조회 (관리자용)
   */
  @Transactional(readOnly = true)
  public long countActiveItems() {
    return itemRepository.countByIsDeletedFalse();
  }

  /**
   * 진행중 거래 건수 조회 (관리자용)
   * - PENDING, CHATTING, TRADE_COMPLETE_REQUESTED 상태 합산
   */
  @Transactional(readOnly = true)
  public long countOngoingTrades() {
    return tradeRequestHistoryRepository.countByTradeStatusIn(
        List.of(TradeStatus.PENDING, TradeStatus.CHATTING, TradeStatus.TRADE_COMPLETE_REQUESTED));
  }

  /**
   * 교환 완료 요청 대기 거래 건수 (사이드바 배지·대시보드 처리 필요, #827)
   */
  @Transactional(readOnly = true)
  public long countCompleteRequestedTrades() {
    return tradeRequestHistoryRepository.countByTradeStatusIn(List.of(TradeStatus.TRADE_COMPLETE_REQUESTED));
  }
}
