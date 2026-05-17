package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminItemService {

  private final ItemRepository itemRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageService chatMessageService;

  /**
   * 관리자용 물품 목록 조회 (페이지네이션, 필터링, 검색 지원)
   */
  @Transactional(readOnly = true)
  public AdminResponse getItemsForAdmin(AdminRequest request) {
    LocalDateTime startDate = parseDate(request.getStartDate());
    LocalDateTime endDate = parseDate(request.getEndDate());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    log.debug("물품 목록 조회: keyword={}, category={}, condition={}, status={}, page={}, size={}",
        request.getSearchKeyword(), request.getItemCategory(), request.getItemCondition(),
        request.getItemStatus(), request.getPageNumber(), request.getPageSize());

    Page<Item> itemPage = itemRepository.findItemsForAdmin(
        request.getSearchKeyword(),
        request.getItemCategory(),
        request.getItemCondition(),
        request.getItemStatus(),
        request.getMinPrice(),
        request.getMaxPrice(),
        startDate,
        endDate,
        pageable
    );

    log.info("물품 목록 조회 완료: totalElements={}, page={}/{}",
        itemPage.getTotalElements(), itemPage.getNumber(), itemPage.getTotalPages());

    return AdminResponse.builder()
        .items(itemPage)
        .totalCount(itemPage.getTotalElements())
        .build();
  }

  /**
   * 최근 등록 물품 조회 (관리자 대시보드용)
   */
  @Transactional(readOnly = true)
  public AdminResponse getRecentItemsForAdmin(int limit) {
    log.debug("최근 등록 물품 조회: limit={}", limit);
    Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdDate"));
    Page<Item> itemPage = itemRepository.findByIsDeletedFalse(pageable);

    log.info("최근 등록 물품 조회 완료: count={}", itemPage.getContent().size());

    return AdminResponse.builder()
        .items(itemPage)
        .totalCount(itemPage.getTotalElements())
        .build();
  }

  @Transactional
  public AdminResponse updateItemStatus(AdminRequest request) {
    // itemStatus null 시 데이터 무결성 문제 방지
    if (request.getItemStatus() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    ItemStatus previousItemStatus = item.getItemStatus();

    log.info("물품 거래 상태 변경: itemId={}, {} -> {}",
        request.getItemId(), previousItemStatus, request.getItemStatus());

    item.setItemStatus(request.getItemStatus());
    itemRepository.save(item);

    // EXCHANGED로 전이될 때만 해당 물품이 포함된 활성 채팅방에 시스템 메시지 전송
    if (request.getItemStatus() == ItemStatus.EXCHANGED && previousItemStatus != ItemStatus.EXCHANGED) {
      notifyRelatedActiveChatRoomsForExchangedItem(item.getItemId());
    }

    return AdminResponse.builder().build();
  }

  private void notifyRelatedActiveChatRoomsForExchangedItem(UUID exchangedItemId) {
    List<TradeRequestHistory> activeChattingHistories =
        tradeRequestHistoryRepository.findActiveChattingHistoriesByItemId(exchangedItemId);

    for (TradeRequestHistory chattingHistory : activeChattingHistories) {
      // takeItem 소유자 = tradeReceiver, giveItem 소유자 = tradeSender
      boolean isExchangedItemTakeItem = chattingHistory.getTakeItem().getItemId().equals(exchangedItemId);

      chatRoomRepository.findByTradeRequestHistoryId(chattingHistory.getTradeRequestHistoryId())
          .ifPresent(chatRoom -> {
            UUID itemOwnerId = isExchangedItemTakeItem
                ? chatRoom.getTradeReceiver().getMemberId()
                : chatRoom.getTradeSender().getMemberId();
            UUID otherPartyId = isExchangedItemTakeItem
                ? chatRoom.getTradeSender().getMemberId()
                : chatRoom.getTradeReceiver().getMemberId();

            log.info("교환완료 물품 관련 채팅방 시스템 메시지 전송: chatRoomId={}, exchangedItemId={}",
                chatRoom.getChatRoomId(), exchangedItemId);
            chatMessageService.sendItemExchangedSystemMessage(chatRoom, itemOwnerId, otherPartyId);
          });
    }
  }

  private LocalDateTime parseDate(String dateString) {
    if (dateString == null || dateString.trim().isEmpty()) {
      return null;
    }
    try {
      LocalDate localDate = LocalDate.parse(dateString.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      return localDate.atStartOfDay();
    } catch (Exception e) {
      log.warn("날짜 파싱 실패: {}", dateString, e);
      return null;
    }
  }
}
