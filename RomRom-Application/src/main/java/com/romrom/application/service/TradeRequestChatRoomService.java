package com.romrom.application.service;

import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래 요청 응답 채팅방 ID 채움 (#858): 응답 내 거래 요청 > 채팅방 일괄 조회 1회 > chatRoomId
 * 아이템 모듈은 채팅 모듈을 모름 > 응용 계층에서 채움 (삭제 대기 방 제외)
 */
@Service
@RequiredArgsConstructor
public class TradeRequestChatRoomService {

  private final ChatRoomRepository chatRoomRepository;

  @Transactional(readOnly = true)
  public TradeResponse fillChatRoomIds(TradeResponse tradeResponse) {
    List<TradeRequestHistory> tradeRequestHistories = collectTradeRequestHistories(tradeResponse);
    if (tradeRequestHistories.isEmpty()) {
      return tradeResponse;
    }

    List<UUID> tradeRequestHistoryIds = tradeRequestHistories.stream()
        .map(TradeRequestHistory::getTradeRequestHistoryId)
        .filter(Objects::nonNull)
        .distinct()
        .toList();
    Map<UUID, UUID> chatRoomIdByTradeRequestHistoryId = chatRoomRepository
        .findAllByTradeRequestHistoryIds(tradeRequestHistoryIds).stream()
        .filter(chatRoom -> chatRoom.getDeletedAt() == null)
        .collect(Collectors.toMap(
            chatRoom -> chatRoom.getTradeRequestHistory().getTradeRequestHistoryId(),
            ChatRoom::getChatRoomId,
            (firstChatRoomId, duplicateChatRoomId) -> firstChatRoomId));

    tradeRequestHistories.forEach(tradeRequestHistory -> tradeRequestHistory.setChatRoomId(
        chatRoomIdByTradeRequestHistoryId.get(tradeRequestHistory.getTradeRequestHistoryId())));
    return tradeResponse;
  }

  // 응답 안 거래 요청 (상세 1건 + 목록 페이지)
  private List<TradeRequestHistory> collectTradeRequestHistories(TradeResponse tradeResponse) {
    List<TradeRequestHistory> tradeRequestHistories = new ArrayList<>();
    if (tradeResponse.getTradeRequestHistory() != null) {
      tradeRequestHistories.add(tradeResponse.getTradeRequestHistory());
    }
    if (tradeResponse.getTradeRequestHistoryPage() != null) {
      tradeRequestHistories.addAll(tradeResponse.getTradeRequestHistoryPage().getContent());
    }
    return tradeRequestHistories;
  }
}
