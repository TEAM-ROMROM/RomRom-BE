package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTradeService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatMessageService chatMessageService;
  private final ChatRoomService chatRoomService;

  /**
   * 관리자용 거래 이력 목록 조회 (상태/기간/검색어 필터, 페이지네이션)
   */
  @Transactional(readOnly = true)
  public AdminResponse getTradesForAdmin(AdminRequest request) {
    LocalDateTime startDate = parseDate(request.getStartDate());
    LocalDateTime endDate = parseDate(request.getEndDate());

    // 빈 문자열 검색어는 null로 정규화 (match-all '%%' 방지 + 불필요한 LIKE 연산 회피)
    String searchKeyword = normalizeSearchKeyword(request.getSearchKeyword());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    log.debug("거래 목록 조회: tradeStatus={}, keyword={}, page={}, size={}",
        request.getTradeStatus(), searchKeyword,
        request.getPageNumber(), request.getPageSize());

    Page<TradeRequestHistory> tradePage = tradeRequestHistoryRepository.findTradesForAdmin(
        request.getTradeStatus(),
        startDate,
        endDate,
        searchKeyword,
        pageable
    );

    log.info("거래 목록 조회 완료: totalElements={}, page={}/{}",
        tradePage.getTotalElements(), tradePage.getNumber(), tradePage.getTotalPages());

    return AdminResponse.builder()
        .trades(tradePage)
        .totalCount(tradePage.getTotalElements())
        .totalPages(tradePage.getTotalPages())
        .totalElements(tradePage.getTotalElements())
        .currentPage(tradePage.getNumber())
        .build();
  }

  /**
   * 관리자용 거래 상세 조회 (양쪽 물품/회원 정보 + 연결된 채팅방)
   */
  @Transactional(readOnly = true)
  public AdminResponse getTradeDetail(AdminRequest request) {
    TradeRequestHistory trade = tradeRequestHistoryRepository
        .findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    Optional<ChatRoom> chatRoom = chatRoomRepository
        .findByTradeRequestHistoryId(trade.getTradeRequestHistoryId());

    // 채팅방이 있으면 전체 메시지를 시간순(오름차순)으로 조회 — 관리자 분쟁/신고 추적용
    // 채팅 조회 실패가 거래 상세 전체를 막지 않도록 best-effort로 감싸 빈 리스트 fallback
    List<ChatMessage> chatMessages = Collections.emptyList();
    if (chatRoom.isPresent()) {
      try {
        chatMessages = chatMessageRepository
            .findByChatRoomIdOrderByCreatedDateAsc(chatRoom.get().getChatRoomId());
      } catch (Exception e) {
        log.warn("관리자 거래 상세 - 채팅 메시지 조회 실패 (빈 리스트로 대체): chatRoomId={}, error={}",
            chatRoom.get().getChatRoomId(), e.getMessage());
      }
    }

    log.info("관리자 거래 상세 조회: tradeRequestHistoryId={}, tradeStatus={}, hasChatRoom={}, chatMessageCount={}",
        trade.getTradeRequestHistoryId(), trade.getTradeStatus(), chatRoom.isPresent(), chatMessages.size());

    return AdminResponse.builder()
        .tradeDetail(AdminResponse.AdminTradeDetailDto.builder()
            .tradeRequestHistory(trade)
            .chatRoom(chatRoom.orElse(null))
            .chatMessages(chatMessages)
            .build())
        .build();
  }

  /**
   * 관리자 거래 강제 취소
   * - TRADED/CANCELED 상태는 불가
   * - 채팅방이 있으면 Hard Delete (ChatRoom + ChatMessage + ChatUserState 전부 삭제)
   * - PENDING 상태(채팅방 없음)는 상태 변경만 수행
   */
  @Transactional
  public AdminResponse forceCancel(AdminRequest request) {
    TradeRequestHistory trade = tradeRequestHistoryRepository
        .findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    TradeStatus currentStatus = trade.getTradeStatus();
    validateForceCancelable(trade.getTradeRequestHistoryId(), currentStatus);

    log.info("관리자 거래 강제 취소: tradeRequestHistoryId={}, {} -> CANCELED, reason={}",
        trade.getTradeRequestHistoryId(), currentStatus, request.getAdminTradeForceReason());

    trade.setTradeStatus(TradeStatus.CANCELED);

    // 채팅방이 있으면 Hard Delete (PENDING은 채팅방 없어 ifPresent로 안전 처리)
    chatRoomRepository.findByTradeRequestHistoryId(trade.getTradeRequestHistoryId())
        .ifPresent(chatRoom -> {
          log.info("관리자 강제 취소 - 채팅방 삭제: chatRoomId={}", chatRoom.getChatRoomId());
          chatRoomService.adminForceDeleteChatRoom(chatRoom.getChatRoomId());
        });

    return AdminResponse.builder().build();
  }

  /**
   * 관리자 거래 강제 완료
   * - CHATTING/TRADE_COMPLETE_REQUESTED 상태만 가능
   * - completeTrade()로 양쪽 물품 EXCHANGED 처리 및 채팅방 시스템 메시지 전송
   */
  @Transactional
  public AdminResponse forceComplete(AdminRequest request) {
    TradeRequestHistory trade = tradeRequestHistoryRepository
        .findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));

    TradeStatus currentStatus = trade.getTradeStatus();
    validateForceCompletable(trade.getTradeRequestHistoryId(), currentStatus);

    log.info("관리자 거래 강제 완료: tradeRequestHistoryId={}, {} -> TRADED, reason={}",
        trade.getTradeRequestHistoryId(), currentStatus, request.getAdminTradeForceReason());

    // 양쪽 물품 EXCHANGED 상태 처리 + tradeStatus TRADED 설정
    trade.completeTrade();

    // 채팅방에 교환 완료 시스템 메시지 전송
    chatRoomRepository.findByTradeRequestHistoryId(trade.getTradeRequestHistoryId())
        .ifPresent(chatRoom -> {
          log.info("강제 완료 채팅방 시스템 메시지 전송: chatRoomId={}", chatRoom.getChatRoomId());
          chatMessageService.sendTradeSystemMessage(
              chatRoom,
              chatRoom.getTradeReceiver().getMemberId(),
              chatRoom.getTradeSender().getMemberId(),
              MessageType.TRADE_COMPLETED,
              "운영자에 의해 교환 완료 처리되었습니다."
          );
        });

    return AdminResponse.builder().build();
  }

  private void validateForceCancelable(UUID tradeId, TradeStatus currentStatus) {
    if (currentStatus == TradeStatus.TRADED) {
      log.error("이미 완료된 거래는 강제 취소할 수 없습니다. tradeRequestHistoryId={}", tradeId);
      throw new CustomException(ErrorCode.TRADE_ALREADY_COMPLETED);
    }
    if (currentStatus == TradeStatus.CANCELED) {
      log.error("이미 취소된 거래입니다. tradeRequestHistoryId={}", tradeId);
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }
  }

  private void validateForceCompletable(UUID tradeId, TradeStatus currentStatus) {
    if (currentStatus == TradeStatus.TRADED) {
      log.error("이미 완료된 거래입니다. tradeRequestHistoryId={}", tradeId);
      throw new CustomException(ErrorCode.TRADE_ALREADY_COMPLETED);
    }
    if (currentStatus == TradeStatus.CANCELED) {
      log.error("취소된 거래는 강제 완료할 수 없습니다. tradeRequestHistoryId={}", tradeId);
      throw new CustomException(ErrorCode.TRADE_ALREADY_PROCESSED);
    }
    if (currentStatus == TradeStatus.PENDING) {
      log.error("PENDING 상태 거래는 채팅방이 없어 강제 완료할 수 없습니다. tradeRequestHistoryId={}", tradeId);
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }

  private String normalizeSearchKeyword(String rawKeyword) {
    if (rawKeyword == null || rawKeyword.trim().isEmpty()) {
      return null;
    }
    return rawKeyword.trim();
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
