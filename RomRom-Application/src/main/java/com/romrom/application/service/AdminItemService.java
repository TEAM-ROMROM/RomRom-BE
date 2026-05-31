package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.ai.service.EmbeddingService;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.service.ItemService;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.repository.ItemReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
  private final ChatRoomService chatRoomService;
  private final ItemService itemService;
  private final ItemReportRepository itemReportRepository;
  private final EmbeddingService embeddingService;

  /**
   * 관리자용 물품 삭제
   * cascade 순서: chat_room Hard Delete → itemService(FCM 수집·TRH CANCELED·Item soft delete)
   *
   * 순서가 중요한 이유:
   * - chat_room은 trade_request_history에 FK를 가지므로 TRH 처리 전에 먼저 제거해야 함
   * - itemService 호출 시 TRH가 아직 DB에 존재해야 FCM 수신자 목록을 온전히 수집 가능
   *
   * trade_request_history는 Hard Delete 하지 않는다:
   * - itemService 내부에서 tradeStatus=CANCELED 처리(논리 삭제)로 충분
   * - Hard Delete 시 trade_review가 FK로 참조 중이므로 DataIntegrityViolationException 발생
   */
  @Transactional
  public void deleteItemByAdmin(UUID itemId, ItemAdminDeleteReason adminDeleteReason, String adminDeleteDetail) {
    if (adminDeleteReason == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    log.info("관리자 물품 삭제 시작: itemId={}, reason={}", itemId, adminDeleteReason);

    // 1. 해당 물품과 연결된 거래 이력 조회
    List<TradeRequestHistory> relatedTradeHistories =
        tradeRequestHistoryRepository.findAllWithMembersByItemId(itemId);

    // 2. chat_room Hard Delete (TRH보다 먼저 — FK 제약: chat_room → trade_request_history)
    for (TradeRequestHistory tradeHistory : relatedTradeHistories) {
      chatRoomRepository.findByTradeRequestHistoryId(tradeHistory.getTradeRequestHistoryId())
          .ifPresent(chatRoom -> {
            log.info("관리자 물품 삭제 - 채팅방 Hard Delete: chatRoomId={}", chatRoom.getChatRoomId());
            chatRoomService.adminForceDeleteChatRoom(chatRoom.getChatRoomId());
          });
    }

    // 3. ItemService 위임: TRH CANCELED 처리 + 이미지/임베딩/좋아요/숨김/soft delete + FCM 알림
    //    TRH가 아직 DB에 존재하므로 FCM 수신자(거래 상대방) 수집이 정상 동작
    itemService.deleteItemByAdmin(itemId, adminDeleteReason, adminDeleteDetail);

    log.info("관리자 물품 삭제 완료: itemId={}", itemId);
  }

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

  /**
   * 관리자용 물품 상세 조회 (이미지 + 거래 이력 + 신고 이력 포함)
   */
  @Transactional(readOnly = true)
  public AdminResponse getItemDetail(AdminRequest request) {
    // OSIV=false 환경: itemImages(LAZY)는 트랜잭션 내에서 페치 조인으로 미리 로드해야 함
    Item item = itemRepository.findByItemIdWithImagesAndMember(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    List<TradeRequestHistory> tradeHistories =
        tradeRequestHistoryRepository.findAllWithMembersByItemId(item.getItemId());

    List<ItemReport> itemReports =
        itemReportRepository.findByItemItemIdOrderByCreatedDateDesc(item.getItemId());

    log.info("관리자 물품 상세 조회: itemId={}, tradeHistories={}, itemReports={}",
        item.getItemId(), tradeHistories.size(), itemReports.size());

    return AdminResponse.builder()
        .itemDetail(AdminResponse.AdminItemDetailDto.builder()
            .item(item)
            .tradeHistories(tradeHistories)
            .itemReports(itemReports)
            .build())
        .build();
  }

  /**
   * 관리자용 물품 카테고리/가격 수정 (운영 보정용)
   */
  @Transactional
  public AdminResponse updateItem(AdminRequest request) {
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    boolean shouldUpdateEmbedding = false;

    if (StringUtils.hasText(request.getItemName())) {
      log.info("관리자 물품명 변경: itemId={}, {} -> {}",
          item.getItemId(), item.getItemName(), request.getItemName());
      item.setItemName(request.getItemName().trim());
      shouldUpdateEmbedding = true;
    }

    if (request.getItemDescription() != null) {
      log.info("관리자 물품 설명 변경: itemId={}", item.getItemId());
      item.setItemDescription(request.getItemDescription().trim());
      shouldUpdateEmbedding = true;
    }

    if (request.getItemCategory() != null) {
      log.info("관리자 물품 카테고리 변경: itemId={}, {} -> {}",
          item.getItemId(), item.getItemCategory(), request.getItemCategory());
      item.setItemCategory(request.getItemCategory());
    }

    if (request.getItemCondition() != null) {
      log.info("관리자 물품 상태 변경: itemId={}, {} -> {}",
          item.getItemId(), item.getItemCondition(), request.getItemCondition());
      item.setItemCondition(request.getItemCondition());
    }

    if (request.getPrice() != null) {
      log.info("관리자 물품 가격 변경: itemId={}, {} -> {}",
          item.getItemId(), item.getPrice(), request.getPrice());
      item.setPrice(request.getPrice());
    }

    itemRepository.save(item);
    if (shouldUpdateEmbedding) {
      embeddingService.updateItemEmbedding(extractItemText(item), item.getItemId());
    }
    return AdminResponse.builder().build();
  }

  /**
   * 관리자용 물품 노출 차단 (isAdminHidden = true)
   */
  @Transactional
  public AdminResponse hideItem(AdminRequest request) {
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    log.info("관리자 물품 노출 차단: itemId={}, reason={}", item.getItemId(), request.getAdminHideReason());

    item.setIsAdminHidden(true);
    item.setAdminHideReason(request.getAdminHideReason());
    itemRepository.save(item);

    return AdminResponse.builder().build();
  }

  /**
   * 관리자용 물품 노출 차단 해제 (isAdminHidden = false)
   */
  @Transactional
  public AdminResponse unhideItem(AdminRequest request) {
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));

    log.info("관리자 물품 노출 차단 해제: itemId={}", item.getItemId());

    item.setIsAdminHidden(false);
    item.setAdminHideReason(null);
    itemRepository.save(item);

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

  private String extractItemText(Item item) {
    return item.getItemName() + ", " + item.getItemDescription();
  }
}
