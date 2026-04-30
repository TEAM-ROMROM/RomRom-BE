package com.romrom.item.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.constant.TradeRequestSortField;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberBlockService;
import com.romrom.notification.event.TradeRequestReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRequestService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ItemRepository itemRepository;
  private final EmbeddingRepository embeddingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberBlockService memberBlockService;

  // 거래 요청 존재 여부 확인
  @Transactional
  public TradeResponse checkTradeRequest(TradeRequest tradeRequest) {
    Item giveItem = findItemById(tradeRequest.getGiveItemId());
    Item takeItem = findItemById(tradeRequest.getTakeItemId());
    verifyItemOwner(tradeRequest.getMember(), giveItem);

    return TradeResponse.builder()
      .tradeRequestHistoryExists(tradeRequestHistoryRepository.existsTradeRequestBetweenItems(takeItem.getItemId(), giveItem.getItemId()))
      .build();
  }

  // 거래 요청 보내기
  @Transactional
  public void sendTradeRequest(TradeRequest request) {
    // giveItem -> takeItem
    Item giveItem = findItemById(request.getGiveItemId());
    Item takeItem = findItemById(request.getTakeItemId());

    verifyItemOwner(request.getMember(), giveItem);
    memberBlockService.verifyNotBlocked(giveItem.getMember().getMemberId(), takeItem.getMember().getMemberId());

    if (giveItem.getMember().getMemberId().equals(takeItem.getMember().getMemberId())) {
      log.error("자신의 물품에 거래 요청을 보낼 수 없습니다. memberId={}", request.getMember().getMemberId());
      throw new CustomException(ErrorCode.TRADE_TO_SELF_FORBIDDEN);
    }

    if (giveItem.getItemStatus() != ItemStatus.AVAILABLE
      || takeItem.getItemStatus() != ItemStatus.AVAILABLE) {
      log.error(
        "거래 요청에 포함된 물품 중 거래 불가능한 상태의 물품이 있습니다. giveItemId={}, giveItemStatus={}, takeItemId={}, takeItemStatus={}",
        giveItem.getItemId(), giveItem.getItemStatus(), takeItem.getItemId(), takeItem.getItemStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    if (tradeRequestHistoryRepository.existsTradeRequestBetweenItems(takeItem.getItemId(), giveItem.getItemId())) {
      log.error("이미 거래 요청이 존재합니다. takeItemId={}, giveItemId={}", takeItem.getItemId(), giveItem.getItemId());
      throw new CustomException(ErrorCode.ALREADY_REQUESTED_ITEM);
    }

    // TradeRequestHistory 엔티티 저장
    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
      .takeItem(takeItem)
      .giveItem(giveItem)
      .itemTradeOptions(request.getItemTradeOptions())
      .tradeStatus(TradeStatus.PENDING)
      .isNew(true)
      .build();
    tradeRequestHistoryRepository.save(tradeRequestHistory);
    log.debug("거래 요청 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());

    // 거래 요청 알림 발송
    TradeRequestReceivedEvent event = new TradeRequestReceivedEvent(
      tradeRequestHistory.getTradeRequestHistoryId(),
      takeItem.getMember().getMemberId(),
      takeItem.getItemName(),
      giveItem.getMember().getNickname(),
      giveItem.getItemId(),
      giveItem.getItemImages().get(0).getImageUrl()
    );
    eventPublisher.publishEvent(event);
  }

  /**
   * 거래 요청 상세조회
   *
   * @param request tradeRequestHistoryId
   */
  @Transactional
  public TradeResponse getTradeRequest(TradeRequest request) {
    TradeRequestHistory history = tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
    UUID requesterId = history.getGiveItem().getMember().getMemberId();
    UUID requestedMemberId = history.getTakeItem().getMember().getMemberId();
    memberBlockService.verifyNotBlocked(requesterId, requestedMemberId);

    // 요청 받은 사람이 조회 시 isNew = false 변경
    if (request.getMember().getMemberId().equals(requestedMemberId)) {
      history.setIsNew(false);
    }

    return TradeResponse.builder()
      .tradeRequestHistory(history)
      .build();
  }

  // 거래 요청 취소
  @Transactional
  public void cancelTradeRequest(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();
    memberBlockService.verifyNotBlocked(giveItem.getMember().getMemberId(), takeItem.getMember().getMemberId());
    Member member = request.getMember();

    if (!takeItem.getMember().getMemberId().equals(member.getMemberId())
      && !giveItem.getMember().getMemberId().equals(member.getMemberId())) {
      log.error("거래 요청을 보낸 사람 또는 받은 사람만 접근할 수 있습니다. memberId={}", member.getMemberId());
      throw new CustomException(ErrorCode.TRADE_ACCESS_FORBIDDEN);
    }

    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("거래 요청이 이미 처리되었습니다. 취소할 수 없습니다. tradeRequestHistoryId={}, currentStatus={}",
        tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    tradeRequestHistory.setTradeStatus(TradeStatus.CANCELED);
    log.debug("거래 요청 취소 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 거절 (물리 삭제)
  @Transactional
  public void rejectTradeRequest(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();
    memberBlockService.verifyNotBlocked(giveItem.getMember().getMemberId(), takeItem.getMember().getMemberId());

    // 요청을 받은 사람(takeItem 소유자)만 거절 가능
    verifyItemOwner(request.getMember(), takeItem);

    // PENDING 상태에서만 거절 가능
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("거래 요청이 이미 처리되었습니다. 거절할 수 없습니다. tradeRequestHistoryId={}, currentStatus={}",
        tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    tradeRequestHistoryRepository.delete(tradeRequestHistory);
    log.debug("거래 요청 거절(물리 삭제) 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 완료로 변경
  @Transactional
  public void completeTrade(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(request.getTradeRequestHistoryId())
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();
    memberBlockService.verifyNotBlocked(giveItem.getMember().getMemberId(), takeItem.getMember().getMemberId());

    // 요청을 받은 사람만 가능
    verifyItemOwner(request.getMember(), takeItem);

    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("거래 요청이 이미 처리되었습니다. 완료할 수 없습니다. tradeRequestHistoryId={}, currentStatus={}",
        tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    tradeRequestHistory.completeTrade();

    log.debug("거래 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 수정
  @Transactional
  public void updateTradeRequest(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(request.getTradeRequestHistoryId())
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();
    memberBlockService.verifyNotBlocked(giveItem.getMember().getMemberId(), takeItem.getMember().getMemberId());

    Member member = request.getMember();

    if (!takeItem.getMember().getMemberId().equals(member.getMemberId())
      && !giveItem.getMember().getMemberId().equals(member.getMemberId())) {
      log.error("거래 당사자만 거래를 완료할 수 있습니다. memberId={}", member.getMemberId());
      throw new CustomException(ErrorCode.TRADE_ACCESS_FORBIDDEN);
    }

    // 취소 또는 완료 상태면 수정 불가
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("거래 요청이 수정할 수 없습니다. tradeRequestHistoryId={}, currentStatus={}",
        tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    // 거래 요청 옵션 수정
    tradeRequestHistory.setItemTradeOptions(request.getItemTradeOptions());
    log.debug("거래 요청 수정 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 받은 요청 리스트
  @Transactional(readOnly = true)
  public TradeResponse getReceivedTradeRequests(TradeRequest request) {
    Item takeItem = findItemById(request.getTakeItemId());
    verifyItemOwner(request.getMember(), takeItem);

    TradeRequestSortField sortField = resolveSortField(request.getSortField());
    Direction sortDirection = resolveSortDirection(request.getSortDirection());

    // AI 추천 순: 임베딩 유사도 기반 in-memory 정렬 (정렬 방향 무시)
    if (sortField == TradeRequestSortField.AI_RECOMMENDED) {
      List<TradeRequestHistory> allHistories = tradeRequestHistoryRepository.findAllByTakeItem(takeItem);
      Page<TradeRequestHistory> sortedPage = sortByAiRecommendation(
        allHistories,
        request.getMember(),
        TradeRequestHistory::getGiveItem,
        request.getPageNumber(),
        request.getPageSize()
      );
      return TradeResponse.builder()
        .tradeRequestHistoryPage(sortedPage)
        .build();
    }

    // 최신순 / 가격순 정렬: Pageable 의 Sort 로 처리
    Pageable pageable = PageRequest.of(
      request.getPageNumber(),
      request.getPageSize(),
      buildSort(sortField, sortDirection, "giveItem.price"));

    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository.findByTakeItem(takeItem, pageable);

    return TradeResponse.builder()
      .tradeRequestHistoryPage(tradeRequestHistoryPage)
      .build();
  }

  // 보낸 요청 리스트
  @Transactional(readOnly = true)
  public TradeResponse getSentTradeRequests(TradeRequest request) {
    Item giveItem = findItemById(request.getGiveItemId());
    verifyItemOwner(request.getMember(), giveItem);

    TradeRequestSortField sortField = resolveSortField(request.getSortField());
    Direction sortDirection = resolveSortDirection(request.getSortDirection());

    // AI 추천 순: 임베딩 유사도 기반 in-memory 정렬 (정렬 방향 무시)
    if (sortField == TradeRequestSortField.AI_RECOMMENDED) {
      List<TradeRequestHistory> allHistories = tradeRequestHistoryRepository.findAllByGiveItem(giveItem);
      Page<TradeRequestHistory> sortedPage = sortByAiRecommendation(
        allHistories,
        request.getMember(),
        TradeRequestHistory::getTakeItem,
        request.getPageNumber(),
        request.getPageSize()
      );
      return TradeResponse.builder()
        .tradeRequestHistoryPage(sortedPage)
        .build();
    }

    // 최신순 / 가격순 정렬: Pageable 의 Sort 로 처리
    Pageable pageable = PageRequest.of(
      request.getPageNumber(),
      request.getPageSize(),
      buildSort(sortField, sortDirection, "takeItem.price"));

    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository
      .findByGiveItem(giveItem, pageable);

    return TradeResponse.builder()
      .tradeRequestHistoryPage(tradeRequestHistoryPage)
      .build();
  }

  /**
   * 정렬 기준 정규화: null 이면 CREATED_DATE 로 폴백
   */
  private TradeRequestSortField resolveSortField(TradeRequestSortField sortField) {
    return sortField == null ? TradeRequestSortField.CREATED_DATE : sortField;
  }

  /**
   * 정렬 방향 정규화: null 이면 DESC 로 폴백
   */
  private Direction resolveSortDirection(Direction sortDirection) {
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
  private Sort buildSort(TradeRequestSortField sortField, Direction sortDirection, String priceSortProperty) {
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
  private Page<TradeRequestHistory> sortByAiRecommendation(
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
   * ID로 Item을 조회하고, 없으면 ITEM_NOT_FOUND
   */
  private Item findItemById(UUID itemId) {
    return itemRepository.findById(itemId)
      .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
  }

  /**
   * 추천 물품 개수 결정
   * TODO: 추후 점수 임계값 계산 도입
   */
  private int setRecommendCount(int totalItemCount) {
    return Math.min(totalItemCount / 2, 3);
  }

  /**
   * takeItem과 giveItem으로 TradeRequestHistory를 조회하고, 없으면 TRADE_REQUEST_NOT_FOUND
   */
  private TradeRequestHistory findTradeRequestHistory(Item takeItem, Item giveItem) {
    return tradeRequestHistoryRepository.findByTakeItemAndGiveItem(takeItem, giveItem)
      .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
  }

  /**
   * Member가 해당 Item의 소유주인지 검증하고, 아니면 INVALID_ITEM_OWNER
   */
  private void verifyItemOwner(Member member, Item item) {
    if (!item.getMember().getMemberId().equals(member.getMemberId())) {
      log.error("해당 물품의 소유자가 아닙니다. memberId={}, itemId={}",
        member.getMemberId(), item.getItemId());
      throw new CustomException(ErrorCode.INVALID_ITEM_OWNER);
    }
  }
}
