package com.romrom.application.scheduler;

import com.romrom.item.service.ItemReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약중 물품 정리 (#859): 1분마다 진행 중 거래 없는 물품 예약 해제
 * 거래 종료 경로(채팅방 나가기·관리자 강제 취소/종료·물품 삭제·강제탈퇴 등) 해제를 한 곳에서 처리
 * 블루그린 전환 중 두 인스턴스가 함께 실행해도 같은 결과 (멱등 UPDATE)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemReservationCleanupScheduler {

  private final ItemReservationService itemReservationService;

  @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
  public void releaseReservationsWithoutActiveTrade() {
    try {
      itemReservationService.releaseReservationsWithoutActiveTrade();
    } catch (RuntimeException cleanupException) {
      log.warn("[item reservation cleanup] failed : {}", cleanupException.getMessage());
    }
  }
}
