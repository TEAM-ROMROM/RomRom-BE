package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.service.ChatMessageService;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
  private final ChatMessageService chatMessageService;

  /**
   * 관리자용 거래 이력 목록 조회 (상태/기간/검색어 필터, 페이지네이션)
   */
  @Transactional(readOnly = true)
  public AdminResponse getTradesForAdmin(AdminRequest request) {
    LocalDateTime startDate = parseDate(request.getStartDate());
    LocalDateTime endDate = parseDate(request.getEndDate());

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(request.getSortDirection(), request.getSortBy())
    );

    log.debug("거래 목록 조회: tradeStatus={}, keyword={}, page={}, size={}",
        request.getTradeStatus(), request.getSearchKeyword(),
        request.getPageNumber(), request.getPageSize());

    Page<TradeRequestHistory> tradePage = tradeRequestHistoryRepository.findTradesForAdmin(
        request.getTradeStatus(),
        startDate,
        endDate,
        request.getSearchKeyword(),
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

    log.info("관리자 거래 상세 조회: tradeRequestHistoryId={}, tradeStatus={}, hasChatRoom={}",
        trade.getTradeRequestHistoryId(), trade.getTradeStatus(), chatRoom.isPresent());

    return AdminResponse.builder()
        .tradeDetail(AdminResponse.AdminTradeDetailDto.builder()
            .tradeRequestHistory(trade)
            .chatRoom(chatRoom.orElse(null))
            .build())
        .build();
  }

  /**
   * 관리자 거래 강제 취소
   * - TRADED/CANCELED 상태는 불가
   * - 기존 cancelTradeRequest 흐름과 동일하게 상태만 변경 (채팅 메시지 없음)
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

    // 기존 cancelTradeRequest 흐름과 동일하게 상태만 변경 (채팅 메시지 없음)
    trade.setTradeStatus(TradeStatus.CANCELED);

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
