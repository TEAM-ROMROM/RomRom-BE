package com.romrom.item.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 교환 요청 보낼 내 물품 추천: 거래 성사율 순 정렬, 상대 취향 기반 AI 추천 정렬
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeItemRecommendationService {

  private final ItemRepository itemRepository;
  private final EmbeddingRepository embeddingRepository;

  /**
   * 거래 성사율 높은 순으로 내 물품 정렬
   *
   * @param request UUID takeItemId
   */
  @Transactional(readOnly = true)
  public TradeResponse getSortedByTradeRate(TradeRequest request) {
    // 타겟 임베딩 조회
    Embedding targetEmbedding = embeddingRepository
      .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(request.getTakeItemId(), OriginalType.ITEM)
      .orElseThrow(() -> new CustomException(ErrorCode.EMBEDDING_NOT_FOUND));

    List<UUID> myItemIds = itemRepository.findAllAvailableItemIdsByMember(request.getMember());

    // 페이징된 유사 아이템 ID 조회
    Page<UUID> idPage = embeddingRepository.findSimilarItemIds(
      myItemIds,
      EmbeddingUtil.toVectorLiteral(targetEmbedding.getEmbedding()),
      PageRequest.of(request.getPageNumber(), request.getPageSize())
    );
    log.debug("물품 유사도 검색 완료: pageNumber={}, pageSize={}, totalElements={}",
      request.getPageNumber(), request.getPageSize(), idPage.getTotalElements());

    // ID 묶음으로 Item+Member 한 번에 로딩
    List<UUID> ids = idPage.getContent();
    List<Item> items = itemRepository.findAllWithMemberByItemIdIn(ids);

    // 벡터 검색 순서를 보존하도록 재정렬
    Map<UUID, Item> itemMap = items.stream()
      .collect(Collectors.toMap(Item::getItemId, it -> it));

    List<Item> ordered = ids.stream()
      .map(itemMap::get)
      .filter(Objects::nonNull) // 혹시 빠진 ID가 있으면 스킵(정책에 맞게 처리)
      .collect(Collectors.toList());

    // Page<Item>으로 다시 감싸기 (total은 벡터 페이지 total 사용)
    Page<Item> itemPage = new PageImpl<>(ordered, idPage.getPageable(), idPage.getTotalElements());

    return TradeResponse.builder()
      .itemPage(itemPage)
      .build();
  }

  /**
   * 상대 취향 기반 내 물품 추천 정렬
   *
   * @param request UUID takeItemId
   */
  @Transactional(readOnly = true)
  public TradeResponse getAiRecommendedItems(TradeRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    // 내 모든 물품 ID 조회 (기본 정렬: 최신순)
    List<UUID> myIds = itemRepository.findAllAvailableItemIdsByMember(request.getMember());

    if (myIds.isEmpty()) {
      return TradeResponse.builder()
        .itemPage(new PageImpl<>(Collections.emptyList(), pageable, 0))
        .build();
    }

    // 상대방 선호 카테고리 임베딩 조회
    Member targetMember = itemRepository.findByItemIdWithMember(request.getTakeItemId())
      .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND))
      .getMember();

    // 가장 최신 임베딩 1개만 조회
    Optional<Embedding> targetPref = embeddingRepository
      .findFirstByOriginalIdAndOriginalTypeOrderByCreatedDateDesc(targetMember.getMemberId(), OriginalType.CATEGORY);

    // 상대방 선호 카테고리 임베딩이 없는 경우: 전체 최신순 반환
    if (targetPref.isEmpty()) {
      log.warn("상대방(ID: {})의 선호도 임베딩 없음 -> 기본 최신순 반환", targetMember.getMemberId());
      List<Item> fallbackItems = itemRepository.findAllWithMemberByItemIdIn(myIds);
      fallbackItems.sort(Comparator.comparing(Item::getCreatedDate).reversed());

      int start = (int) pageable.getOffset();
      int end = Math.min(start + pageable.getPageSize(), fallbackItems.size());
      List<Item> pagedFallbacks = (start >= fallbackItems.size()) ? List.of() : fallbackItems.subList(start, end);

      return TradeResponse.builder()
        .itemPage(new PageImpl<>(pagedFallbacks, pageable, fallbackItems.size()))
        .build();
    }

    // pgvector 유사도 검색 실행 (임베딩이 존재하는 것들만 정렬)
    List<UUID> sortedRecommendItemIds = new ArrayList<>(embeddingRepository.findRecommendedItemIds(
      myIds,
      EmbeddingUtil.toVectorLiteral(targetPref.get().getEmbedding()),
      PageRequest.of(0, myIds.size())).getContent());

    // 임베딩 없는 물품 추가
    myIds.forEach(id -> {
      if (!sortedRecommendItemIds.contains(id)) sortedRecommendItemIds.add(id);
    });

    int recommendLimit = setRecommendCount(myIds.size());
    Set<UUID> recommendedItemIdSet = new HashSet<>(
      sortedRecommendItemIds.subList(0, Math.min(recommendLimit, sortedRecommendItemIds.size()))
    );

    int start = (int) pageable.getOffset();
    int end = Math.min(start + pageable.getPageSize(), sortedRecommendItemIds.size());
    List<UUID> pagedItemIds = (start >= sortedRecommendItemIds.size()) ? List.of() : sortedRecommendItemIds.subList(start, end);

    Map<UUID, Item> itemMap = itemRepository.findAllWithMemberByItemIdIn(pagedItemIds).stream()
      .collect(Collectors.toMap(Item::getItemId, Function.identity()));

    List<Item> orderedItems = pagedItemIds.stream()
      .map(itemMap::get)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    orderedItems.forEach(item -> item.setIsRecommended(recommendedItemIdSet.contains(item.getItemId())));

    return TradeResponse.builder()
      .itemPage(new PageImpl<>(orderedItems, pageable, sortedRecommendItemIds.size()))
      .build();
  }

  /**
   * 추천 물품 개수 결정
   * TODO: 추후 점수 임계값 계산 도입
   */
  private int setRecommendCount(int totalItemCount) {
    return Math.min(totalItemCount / 2, 3);
  }
}
