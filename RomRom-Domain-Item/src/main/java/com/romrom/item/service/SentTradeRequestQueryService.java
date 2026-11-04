package com.romrom.item.service;

import com.romrom.common.constant.TradeRequestSortField;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 보낸 요청 전체 조회 (#858): 내 모든 물품 > 한 번에 페이징 (물품 수만큼 /get/sent 반복 호출 대체)
 * 조건·정렬은 물품별 보낸 요청 조회와 같음 (AI 추천순은 정렬 방향 무시)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SentTradeRequestQueryService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final TradeRequestPageSorter tradeRequestPageSorter;

  @Transactional(readOnly = true)
  public TradeResponse getAllSentTradeRequests(TradeRequest request) {
    UUID memberId = request.getMember().getMemberId();
    TradeRequestSortField sortField = tradeRequestPageSorter.resolveSortField(request.getSortField());
    Direction sortDirection = tradeRequestPageSorter.resolveSortDirection(request.getSortDirection());

    // AI 추천 순: 상대 물품 임베딩 유사도 기반 메모리 정렬
    if (sortField == TradeRequestSortField.AI_RECOMMENDED) {
      List<TradeRequestHistory> allSentHistories = tradeRequestHistoryRepository.findAllSentListByGiveItemMemberId(memberId);
      return TradeResponse.builder()
          .tradeRequestHistoryPage(tradeRequestPageSorter.sortByAiRecommendation(
              allSentHistories,
              request.getMember(),
              TradeRequestHistory::getTakeItem,
              request.getPageNumber(),
              request.getPageSize()))
          .build();
    }

    // 최신순 / 가격순 (가격 = 상대 물품 가격)
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        tradeRequestPageSorter.buildSort(sortField, sortDirection, "takeItem.price"));
    Page<TradeRequestHistory> sentHistoryPage = tradeRequestHistoryRepository.findAllSentByGiveItemMemberId(memberId, pageable);
    log.debug("[trade sent all] loaded : memberId={}, totalElements={}", memberId, sentHistoryPage.getTotalElements());
    return TradeResponse.builder()
        .tradeRequestHistoryPage(sentHistoryPage)
        .build();
  }
}
