package com.romrom.item.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.constant.TradeRequestSortField;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

/**
 * 거래 요청 목록 정렬: 정렬 기준·방향 정규화, 최신순·가격순 Sort, AI 추천순 메모리 정렬·페이지
 * 받은·보낸(물품별·전체) 요청 조회 공통
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeRequestPageSorter {

  private final EmbeddingRepository embeddingRepository;

  /**
   * 정렬 기준 정규화: null 이면 CREATED_DATE 로 폴백
   */
  public TradeRequestSortField resolveSortField(TradeRequestSortField sortField) {
    return sortField == null ? TradeRequestSortField.CREATED_DATE : sortField;
  }

  /**
   * 정렬 방향 정규화: null 이면 DESC 로 폴백
   */
  public Direction resolveSortDirection(Direction sortDirection) {
    return sortDirection == null ? Direction.DESC : sortDirection;
  }

  /**
   * CREATED_DATE / PRICE 정렬 기준 + 방향에 대응하는 Sort 객체 생성
   * - 가격 정렬은 동일 가격에 대해 createdDate DESC 보조 정렬 적용
   * - AI_RECOMMENDED 는 별도 처리 (이 메서드 호출 전에 분기)
   *
   * @param sortField         정렬 기준 (CREATED_DATE / PRICE)
   * @param sortDirection     정렬 방향 (ASC / DESC)
   * @param priceSortProperty 가격 정렬 시 사용할 필드 경로 (받은 요청은 giveItem.price, 보낸 요청은 takeItem.price)
   */
  public Sort buildSort(TradeRequestSortField sortField, Direction sortDirection, String priceSortProperty) {
    return switch (sortField) {
      case PRICE -> Sort.by(sortDirection, priceSortProperty)
        .and(Sort.by(Direction.DESC, "createdDate"));
      case CREATED_DATE -> Sort.by(sortDirection, "createdDate");
      case AI_RECOMMENDED -> throw new IllegalStateException(
        "AI_RECOMMENDED 정렬은 sortByAiRecommendation 에서 처리되어야 합니다");
    };
  }

  /**
   * AI 추천 정렬: 내 선호 카테고리 임베딩 기준으로 거래 요청 목록을 상대 물품 유사도 순으로 정렬
   * - 내 선호 임베딩이 없으면 최신순으로 폴백
   * - 임베딩 없는 상대 물품은 후순위(최신순)로 배치
   *
   * @param histories          정렬 대상 거래 요청 목록 (전체)
   * @param member             요청 회원 (선호 임베딩 조회 대상)
   * @param targetItemExtractor 정렬 기준이 되는 상대 물품 추출자 (받은: giveItem, 보낸: takeItem)
   * @param pageNumber         페이지 번호
   * @param pageSize           페이지 크기
   */
  public Page<TradeRequestHistory> sortByAiRecommendation(
    List<TradeRequestHistory> histories,
    Member member,
    Function<TradeRequestHistory, Item> targetItemExtractor,
    int pageNumber,
    int pageSize
  ) {
    Pageable pageable = PageRequest.of(pageNumber, pageSize);

    // 거래 요청이 없으면 빈 페이지
    if (histories.isEmpty()) {
      return new PageImpl<>(Collections.emptyList(), pageable, 0);
    }

    // 내 선호 카테고리 임베딩 조회 (없으면 최신순 폴백)
    Optional<Embedding> myPreferenceEmbedding = embeddingRepository
      .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(member.getMemberId(), OriginalType.CATEGORY);

    if (myPreferenceEmbedding.isEmpty()) {
      log.warn("회원(ID: {})의 선호 카테고리 임베딩 없음 -> AI 추천 정렬을 최신순으로 폴백", member.getMemberId());
      return paginateByCreatedDateDesc(histories, pageable);
    }

    // 상대 물품 ID 수집 (중복 제거하되 거래 요청별 1:1 매핑 유지를 위해 분리)
    List<UUID> targetItemIds = histories.stream()
      .map(targetItemExtractor)
      .map(Item::getItemId)
      .distinct()
      .toList();

    // pgvector 유사도 검색으로 정렬된 상대 물품 ID 리스트 획득
    List<UUID> sortedTargetItemIds = new ArrayList<>(embeddingRepository.findRecommendedItemIds(
      targetItemIds,
      EmbeddingUtil.toVectorLiteral(myPreferenceEmbedding.get().getEmbedding()),
      PageRequest.of(0, targetItemIds.size())
    ).getContent());

    // 임베딩 없는 물품을 후순위로 추가
    targetItemIds.forEach(id -> {
      if (!sortedTargetItemIds.contains(id)) {
        sortedTargetItemIds.add(id);
      }
    });

    // 상대 물품 ID -> 정렬 우선순위 매핑
    Map<UUID, Integer> itemIdToRank = new HashMap<>();
    for (int rank = 0; rank < sortedTargetItemIds.size(); rank++) {
      itemIdToRank.put(sortedTargetItemIds.get(rank), rank);
    }

    // 거래 요청 정렬: 상대 물품 우선순위 -> 동일 우선순위 시 최신순
    List<TradeRequestHistory> sortedHistories = histories.stream()
      .sorted(Comparator
        .<TradeRequestHistory, Integer>comparing(h ->
          itemIdToRank.getOrDefault(targetItemExtractor.apply(h).getItemId(), Integer.MAX_VALUE))
        .thenComparing(Comparator.comparing(TradeRequestHistory::getCreatedDate).reversed()))
      .toList();

    return paginate(sortedHistories, pageable);
  }

  /**
   * 임베딩 폴백용: 최신순 정렬 후 페이지네이션
   */
  private Page<TradeRequestHistory> paginateByCreatedDateDesc(
    List<TradeRequestHistory> histories,
    Pageable pageable
  ) {
    List<TradeRequestHistory> sorted = histories.stream()
      .sorted(Comparator.comparing(TradeRequestHistory::getCreatedDate).reversed())
      .toList();
    return paginate(sorted, pageable);
  }

  /**
   * In-memory 페이지네이션 헬퍼
   */
  private Page<TradeRequestHistory> paginate(List<TradeRequestHistory> sorted, Pageable pageable) {
    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), sorted.size());
    List<TradeRequestHistory> pageContent = (start >= sorted.size()) ? List.of() : sorted.subList(start, end);
    return new PageImpl<>(pageContent, pageable, sorted.size());
  }
}
