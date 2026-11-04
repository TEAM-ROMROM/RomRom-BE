package com.romrom.item.service;

import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.ItemTradeOption;
import com.romrom.common.constant.TradeRequestSortField;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRequestService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ItemRepository itemRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberBlockService memberBlockService;
  private final TradeRequestContentPolicy tradeRequestContentPolicy;
  private final TradeRequestAiSorter tradeRequestAiSorter;
  private final ItemReservationService itemReservationService;

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

    // TradeRequestHistory 엔티티 저장 (추가금·한 줄 메시지는 선택, 미전송 시 null)
    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
      .takeItem(takeItem)
      .giveItem(giveItem)
      .itemTradeOptions(request.getItemTradeOptions())
      .extraChargeAmount(tradeRequestContentPolicy.resolveExtraChargeAmount(
        request.getItemTradeOptions(), request.getExtraChargeAmount()))
      .requestMessage(tradeRequestContentPolicy.resolveRequestMessage(request.getRequestMessage()))
      .tradeStatus(TradeStatus.PENDING)
      .isNew(true)
      .build();
    try {
      tradeRequestHistoryRepository.saveAndFlush(tradeRequestHistory);
    } catch (DataIntegrityViolationException e) {
      log.warn("동시 거래 요청 중복 저장 시도 차단: takeItemId={}, giveItemId={}",
          takeItem.getItemId(), giveItem.getItemId());
      throw new CustomException(ErrorCode.ALREADY_REQUESTED_ITEM);
    }
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

    // 요청 받은 사람이 조회 시 isNew = false + 최초 확인 시각 기록
    if (request.getMember().getMemberId().equals(requestedMemberId)) {
      history.markCheckedByReceiver();
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

  // 거래 요청 거절 (행 보존, REJECTED + 거절 시각)
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

    tradeRequestHistory.reject();
    log.debug("거래 요청 거절 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
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
    itemReservationService.cleanUpAfterTradeCompleted(tradeRequestHistory);

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

    // 거래 요청 옵션 수정 (추가금: 옵션 해제 시 삭제, 미전송 시 유지 / 메시지: 미전송 시 유지)
    tradeRequestHistory.setItemTradeOptions(request.getItemTradeOptions());
    updateExtraChargeAmount(tradeRequestHistory, request);
    if (request.getRequestMessage() != null) {
      tradeRequestHistory.setRequestMessage(tradeRequestContentPolicy.resolveRequestMessage(request.getRequestMessage()));
    }
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
      Page<TradeRequestHistory> sortedPage = tradeRequestAiSorter.sortByAiRecommendation(
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
    boolean isRejectedIncluded = Boolean.TRUE.equals(request.getIncludeRejected());

    TradeRequestSortField sortField = resolveSortField(request.getSortField());
    Direction sortDirection = resolveSortDirection(request.getSortDirection());

    // AI 추천 순: 임베딩 유사도 기반 in-memory 정렬 (정렬 방향 무시)
    if (sortField == TradeRequestSortField.AI_RECOMMENDED) {
      List<TradeRequestHistory> allHistories = tradeRequestHistoryRepository.findAllByGiveItem(giveItem, isRejectedIncluded);
      Page<TradeRequestHistory> sortedPage = tradeRequestAiSorter.sortByAiRecommendation(
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
      .findByGiveItem(giveItem, isRejectedIncluded, pageable);

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
   * 추가금 갱신: EXTRA_CHARGE 해제 > null, 금액 전송 > 검증 후 반영, 미전송 > 기존 값 유지
   */
  private void updateExtraChargeAmount(TradeRequestHistory tradeRequestHistory, TradeRequest request) {
    List<ItemTradeOption> updatedItemTradeOptions = request.getItemTradeOptions();
    boolean hasExtraChargeOption = updatedItemTradeOptions != null
      && updatedItemTradeOptions.contains(ItemTradeOption.EXTRA_CHARGE);
    if (!hasExtraChargeOption) {
      tradeRequestHistory.setExtraChargeAmount(null);
      return;
    }
    if (request.getExtraChargeAmount() != null) {
      tradeRequestHistory.setExtraChargeAmount(tradeRequestContentPolicy.resolveExtraChargeAmount(
        updatedItemTradeOptions, request.getExtraChargeAmount()));
    }
  }

  /**
   * ID로 Item을 조회하고, 없으면 ITEM_NOT_FOUND
   */
  private Item findItemById(UUID itemId) {
    return itemRepository.findByItemIdAndIsDeletedFalse(itemId)
      .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
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
