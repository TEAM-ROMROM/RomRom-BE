package com.romrom.item.service;

import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.TradeRequest;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
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
    Member member = request.getMember();

    Item takeItem = itemRepository.findById(request.getTakeItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
    Item giveItem = itemRepository.findById(request.getGiveItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    // 거래 요청 조회
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository
        .findByTakeItemAndGiveItem(takeItem, giveItem)
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Member takeItemMember = takeItem.getMember();
    Member giveItemMember = giveItem.getMember();

    // 취소 요청을 보낸 멤버가 거래 요청을 보낸 멤버거나 받은 멤버인지 검증
    if (!takeItemMember.getMemberId().equals(member.getMemberId())
        && !giveItemMember.getMemberId().equals(member.getMemberId())) {
      log.error("거래 요청을 보낸 사람 또는 받은 사람만 취소할 수 있습니다. memberId={}", member.getMemberId());
      throw new CustomException(ErrorCode.TRADE_CANCEL_FORBIDDEN);
    }

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
      return TradeResponse.builder()
          .item(giveItem)
          .itemImages(giveItemImages)
          .itemTradeOptions(history.getItemTradeOptions())
          .build();
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
      return TradeResponse.builder()
          .item(takeItem)
          .itemImages(takeItemImages)
          .itemTradeOptions(history.getItemTradeOptions())
          .build();
    });
  }
}
