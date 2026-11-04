package com.romrom.item.service;

import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 물품 예약중 관리: 진행 중 거래 없는 예약 해제, 교환 완료 후 남은 대기 요청 정리 (#859)
 * 예약 시작은 TradeRequestHistory.startChatting(), 해제는 거래 종료 지점 + 주기 정리 스케줄러
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemReservationService {

  private final ItemRepository itemRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;

  /**
   * 진행 중 거래(채팅중·교환완료요청중)가 남지 않은 물품의 예약 해제
   */
  @Transactional
  public int releaseReservationsWithoutActiveTrade() {
    int releasedItemCount = itemRepository.releaseReservationsWithoutActiveTrade();
    if (releasedItemCount > 0) {
      log.info("[item reservation] released : count={}", releasedItemCount);
    }
    return releasedItemCount;
  }

  /**
   * 교환 완료 후 정리: 두 물품의 남은 대기 요청 CANCELED > 예약 해제
   * 알림은 기존 알림 타입 중 맞는 것이 없어 보내지 않음
   */
  @Transactional
  public void cleanUpAfterTradeCompleted(TradeRequestHistory completedTradeRequestHistory) {
    List<UUID> exchangedItemIds = List.of(
        completedTradeRequestHistory.getTakeItem().getItemId(),
        completedTradeRequestHistory.getGiveItem().getItemId());
    int canceledPendingRequestCount = tradeRequestHistoryRepository.cancelAllPendingByItemIds(exchangedItemIds);
    log.info("[trade complete] pending requests canceled : tradeRequestHistoryId={}, count={}",
        completedTradeRequestHistory.getTradeRequestHistoryId(), canceledPendingRequestCount);
    releaseReservationsWithoutActiveTrade();
  }
}
