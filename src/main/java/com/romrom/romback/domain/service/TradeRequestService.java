package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.ItemTradeOption;
import com.romrom.romback.domain.object.constant.TradeStatus;
import com.romrom.romback.domain.object.dto.TradeRequest;
import com.romrom.romback.domain.object.dto.TradeResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.ItemImage;
import com.romrom.romback.domain.object.postgres.TradeRequestHistory;
import com.romrom.romback.domain.repository.postgres.ItemImageRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.domain.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
  private final ItemImageRepository itemImageRepository;

  // 거래 요청 보내기
  @Transactional
  public void sendTradeRequest(TradeRequest request) {
    Item takeItem = itemRepository.findById(request.getTakeItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    Item giveItem = itemRepository.findById(request.getGiveItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 중복 체크
    if (tradeRequestHistoryRepository.existsByTakeItemAndGiveItem(takeItem, giveItem)) {
      throw new CustomException(ErrorCode.ALREADY_REQUESTED_ITEM);
    }

    // TradeRequestHistory 엔티티 저장
    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
        .takeItem(takeItem)
        .giveItem(giveItem)
        .itemTradeOptions(request.getItemTradeOptions())
        .tradeStatus(TradeStatus.PENDING)
        .build();
    tradeRequestHistoryRepository.save(tradeRequestHistory);
    log.info("거래 요청 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 취소
  @Transactional
  public void cancelTradeRequest(TradeRequest request) {
    Item takeItem = itemRepository.findById(request.getTakeItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    Item giveItem = itemRepository.findById(request.getGiveItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 거래 요청 조회
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository
        .findByTakeItemAndGiveItem(takeItem, giveItem)
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    // 거래 요청 취소 상태로 변경
    tradeRequestHistory.setTradeStatus(TradeStatus.CANCELED);
    log.info("거래 요청 취소 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 받은 요청 리스트
  @Transactional(readOnly = true)
  public Page<TradeResponse> getReceivedTradeRequests(TradeRequest request) {
    Item takeItem = itemRepository.findById(request.getTakeItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // Pageable 객체 생성
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate")); // 최신순으로 정렬

    // 해당 물품이 받은 요청이면서 PENDING 상태인 TradeRequestHistory 조회 (페이징 적용)
    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository
        .findByTakeItemAndTradeStatus(takeItem, TradeStatus.PENDING, pageable);

    // TradeResponse 로 변환
    return tradeRequestHistoryPage.map(history -> {
      Item giveItem = history.getGiveItem();
      List<ItemImage> giveItemImages = itemImageRepository.findByItem(giveItem);
      return toTradeResponse(giveItem, giveItemImages, history.getItemTradeOptions());
    });
  }

  // 보낸 요청 리스트
  @Transactional(readOnly = true)
  public Page<TradeResponse> getSentTradeRequests(TradeRequest request) {
    Item giveItem = itemRepository.findById(request.getGiveItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // Pageable 객체 생성
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Direction.DESC, "createdDate")); // 최신순으로 정렬

    // 해당 물품이 보낸 요청이면서 PENDING 상태인 TradeRequestHistory 조회 (페이징 적용)
    Page<TradeRequestHistory> tradeRequestHistoryPage = tradeRequestHistoryRepository
        .findByGiveItemAndTradeStatus(giveItem, TradeStatus.PENDING, pageable);

    // TradeResponse 로 변환
    return tradeRequestHistoryPage.map(history -> {
      Item takeItem = history.getTakeItem();
      List<ItemImage> takeItemImages = itemImageRepository.findByItem(takeItem);
      return toTradeResponse(takeItem, takeItemImages, history.getItemTradeOptions());
    });
  }

  // TradeResponse 로 변환
  private TradeResponse toTradeResponse(Item item, List<ItemImage> itemImages, List<ItemTradeOption> tradeOptions) {
    return TradeResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .itemTradeOptions(tradeOptions)
        .build();
  }
}
