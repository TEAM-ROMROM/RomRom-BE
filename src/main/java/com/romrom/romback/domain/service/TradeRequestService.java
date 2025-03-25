package com.romrom.romback.domain.service;

import com.romrom.romback.domain.object.constant.TradeOption;
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
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    Item requestedItem = validateItem(request.getRequestedItemId());
    Item requestingItem = validateItem(request.getRequestingItemId());

    // 중복 체크
    if (tradeRequestHistoryRepository.existsByRequestedItemAndRequestingItem(requestedItem, requestingItem)) {
      log.error("이미 거래 요청을 보낸 물품입니다. requestItemId:{}, requestingItemId:{}",
          requestedItem.getItemId(), requestingItem.getItemId());
      throw new CustomException(ErrorCode.ALREADY_REQUESTED_ITEM);
    }

    // TradeRequestHistory 엔티티 저장
    TradeRequestHistory tradeRequestHistory = TradeRequestHistory.builder()
        .requestedItem(requestedItem)
        .requestingItem(requestingItem)
        .tradeOptions(request.getTradeOptions())
        .build();
    tradeRequestHistoryRepository.save(tradeRequestHistory);
    log.info("거래 요청 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 거래 요청 취소
  @Transactional
  public void cancelTradeRequest(TradeRequest request) {
    Item requestedItem = validateItem(request.getRequestedItemId());
    Item requestingItem = validateItem(request.getRequestingItemId());

    // 거래 요청 조회
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository
        .findByRequestedItemAndRequestingItem(requestedItem, requestingItem)
        .orElseThrow(() -> {
          log.error("취소하려는 거래 요청이 존재하지 않습니다. requestedItemId={}, requestingItemId={}",
              requestedItem.getItemId(), requestingItem.getItemId());
          return new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND);
        });

    // 거래 요청 삭제
    tradeRequestHistoryRepository.delete(tradeRequestHistory);
    log.info("거래 요청 취소 완료: tradeRequestHistoryId={}", tradeRequestHistory.getTradeRequestHistoryId());
  }

  // 받은 요청 리스트
  @Transactional(readOnly = true)
  public List<TradeResponse> getReceivedTradeRequests(TradeRequest request) {
    Item requestedItem = validateItem(request.getRequestedItemId());
    // 해당 물품이 받은 요청인 TradeRequestHistory 조회
    List<TradeRequestHistory> tradeRequestHistoryList = tradeRequestHistoryRepository.findByRequestedItem(requestedItem);

    // 해당 물품에 요청을 보낸 물품, 그 물품에 해당하는 이미지, 거래옵션 TradeResponse 로 변환
    return tradeRequestHistoryList.stream()
        .map(history -> {
          Item requestingItem = history.getRequestingItem();
          List<ItemImage> requestingItemImages = itemImageRepository.findByItem(requestingItem);
          return toTradeResponse(requestingItem, requestingItemImages, history.getTradeOptions());
        })
        .collect(Collectors.toList());
  }

  // 보낸 요청 리스트
  @Transactional(readOnly = true)
  public List<TradeResponse> getSentTradeRequests(TradeRequest request) {
    Item requestingItem = validateItem(request.getRequestingItemId());
    // 해당 물품이 보낸 요청인 TradeRequestHistory 조회
    List<TradeRequestHistory> tradeRequestHistoryList = tradeRequestHistoryRepository.findByRequestingItem(requestingItem);

    // 해당 물품이 요청을 보낸 물품, 그 물품에 해당하는 이미지, 거래옵션 TradeResponse 로 변환
    return tradeRequestHistoryList.stream()
        .map(history -> {
          Item requestedItem = history.getRequestedItem();
          List<ItemImage> requestedItemImages = itemImageRepository.findByItem(requestedItem);
          return toTradeResponse(requestedItem, requestedItemImages, history.getTradeOptions());
        })
        .collect(Collectors.toList());
  }

  // 물품 검증
  private Item validateItem(UUID itemId) {
    return itemRepository.findById(itemId)
        .orElseThrow(() -> {
          log.error("해당 물품을 찾을 수 없습니다. itemId={}", itemId);
          return new CustomException(ErrorCode.ITEM_NOT_FOUND);
        });
  }

  // TradeResponse 로 변환
  private TradeResponse toTradeResponse(Item item, List<ItemImage> itemImages, List<TradeOption> tradeOptions) {
    return TradeResponse.builder()
        .item(item)
        .itemImages(itemImages)
        .tradeOptions(tradeOptions)
        .build();
  }
}
