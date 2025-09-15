package com.romrom.item.service;

import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.entity.postgres.Embedding;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.repository.EmbeddingRepository;
import com.romrom.item.dto.ItemDetail;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeRequestDetail;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeRequestService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ItemRepository itemRepository;
  private final EmbeddingRepository embeddingRepository;
  private final ItemDetailAssembler itemDetailAssembler;

  // 거래 요청 보내기
  @Transactional
  public void sendTradeRequest(TradeRequest request) {
    // giveItem -> takeItem
    Item giveItem = findItemById(request.getGiveItemId());
    Item takeItem = findItemById(request.getTakeItemId());

    verifyItemOwner(request.getMember(), giveItem);

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

    if (tradeRequestHistoryRepository.existsByTakeItemAndGiveItem(takeItem, giveItem)) {
      log.error("이미 거래 요청이 존재합니다. takeItemId={}, giveItemId={}", takeItem.getItemId(), giveItem.getItemId());
      throw new CustomException(ErrorCode.ALREADY_REQUESTED_ITEM);
    }

    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
        .takeItem(takeItem)
        .giveItem(giveItem)
        .itemTradeOptions(request.getItemTradeOptions())
        .tradeStatus(TradeStatus.PENDING)
        .build();
    tradeRequestHistoryRepository.save(tradeRequestHistory);
    log.debug("거래 요청 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 취소
  @Transactional
  public void cancelTradeRequest(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(request.getTradeHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();

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

  // 거래 완료로 변경
  @Transactional
  public void completeTrade(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(request.getTradeHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();

    // 요청을 받은 사람만 가능
    verifyItemOwner(request.getMember(), takeItem);

    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("거래 요청이 이미 처리되었습니다. 완료할 수 없습니다. tradeRequestHistoryId={}, currentStatus={}",
          tradeRequestHistory.getTradeRequestHistoryId(), tradeRequestHistory.getTradeStatus());
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }

    // 거래 완료 상태로 변경
    tradeRequestHistory.setTradeStatus(TradeStatus.TRADED);

    // 물품 상태 변경
    takeItem.setItemStatus(ItemStatus.EXCHANGED);
    giveItem.setItemStatus(ItemStatus.EXCHANGED);

    log.debug("거래 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 수정
  @Transactional
  public void updateTradeRequest(TradeRequest request) {
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(request.getTradeHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Item takeItem = tradeRequestHistory.getTakeItem();
    Item giveItem = tradeRequestHistory.getGiveItem();

    // 요청을 보낸 사람만 수정 가능
    verifyItemOwner(request.getMember(), giveItem);

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

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate")); // 최신순으로 정렬

    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository
        .findByTakeItemAndTradeStatus(takeItem, TradeStatus.PENDING, pageable);

    // 요청 보낸 사람들의 물품들
    List<Item> giveItems = tradeRequestHistoryPage.getContent().stream()
        .map(TradeRequestHistory::getGiveItem)
        .collect(Collectors.toList());

    Page<Item> giveItemPage = new PageImpl<>(giveItems, pageable, tradeRequestHistoryPage.getTotalElements());

    Page<ItemDetail> itemDetailPage = itemDetailAssembler.assembleForAllItems(giveItemPage);

    List<TradeRequestHistory> tradeRequestHistories = tradeRequestHistoryPage.getContent();
    List<ItemDetail> itemDetails = itemDetailPage.getContent();

    List<TradeRequestDetail> tradeRequestDetails = java.util.stream.IntStream.range(0, tradeRequestHistories.size())
        .mapToObj(i -> {
          TradeRequestHistory history = tradeRequestHistories.get(i);
          ItemDetail itemDetail = itemDetails.get(i);
          return TradeRequestDetail.builder()
              .tradeRequestHistoryId(history.getTradeRequestHistoryId())
              .itemTradeOptions(history.getItemTradeOptions())
              .itemDetail(itemDetail)
              .build();
        })
        .collect(Collectors.toList());

    Page<TradeRequestDetail> tradeRequestDetailPage = new PageImpl<>(tradeRequestDetails, pageable, tradeRequestHistoryPage.getTotalElements());

    return TradeResponse.builder()
        .tradeRequestDetailPage(tradeRequestDetailPage)
        .build();
  }

  // 보낸 요청 리스트
  @Transactional(readOnly = true)
  public TradeResponse getSentTradeRequests(TradeRequest request) {
    Item giveItem = findItemById(request.getGiveItemId());
    verifyItemOwner(request.getMember(), giveItem);

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate"));

    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository
        .findByGiveItem(giveItem, pageable);

    // 내가 요청 보낸 물품들
    List<Item> takeItems = tradeRequestHistoryPage.getContent().stream()
        .map(TradeRequestHistory::getTakeItem)
        .collect(Collectors.toList());

    Page<Item> takeItemPage = new PageImpl<>(takeItems, pageable, tradeRequestHistoryPage.getTotalElements());

    Page<ItemDetail> itemDetailPage = itemDetailAssembler.assembleForAllItems(takeItemPage);

    List<TradeRequestHistory> tradeRequestHistories = tradeRequestHistoryPage.getContent();
    List<ItemDetail> itemDetails = itemDetailPage.getContent();

    List<TradeRequestDetail> tradeRequestDetails = java.util.stream.IntStream.range(0, tradeRequestHistories.size())
        .mapToObj(i -> {
          TradeRequestHistory history = tradeRequestHistories.get(i);
          ItemDetail itemDetail = itemDetails.get(i);
          return TradeRequestDetail.builder()
              .tradeRequestHistoryId(history.getTradeRequestHistoryId())
              .itemTradeOptions(history.getItemTradeOptions())
              .itemDetail(itemDetail)
              .build();
        })
        .collect(Collectors.toList());

    Page<TradeRequestDetail> tradeRequestDetailPage = new PageImpl<>(tradeRequestDetails, pageable, tradeRequestHistoryPage.getTotalElements());

    return TradeResponse.builder()
        .tradeRequestDetailPage(tradeRequestDetailPage)
        .build();
  }

  @Transactional(readOnly = true)
  public TradeResponse getSortedByTradeRate(TradeRequest request) {
    // 타겟 임베딩 조회
    Embedding targetEmbedding = embeddingRepository
        .findByOriginalIdAndOriginalType(request.getTakeItemId(), OriginalType.ITEM)
        .orElseThrow(() -> new CustomException(ErrorCode.EMBEDDING_NOT_FOUND));

    List<UUID> myItemIds = itemRepository.findAllItemIdsByMember(request.getMember());

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

    // 공용 어셈블러로 통일 (이미 member 로딩됨)
    return TradeResponse.builder()
        .itemDetailPage(itemDetailAssembler.assembleForAllItems(itemPage))
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