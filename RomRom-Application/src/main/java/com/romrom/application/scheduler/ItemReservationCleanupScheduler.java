package com.romrom.application.scheduler;

import com.romrom.common.constant.ServiceTimeZone;
import com.romrom.item.service.ItemReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 예약중 물품 정리: 1분마다 진행 중 거래 없는 물품 예약 해제 (거래 종료 경로 공통: 채팅방 나가기·관리자 강제 처리·물품 삭제·강제탈퇴 등, #859)
 * 멱등 UPDATE > 블루그린 두 인스턴스 동시 실행에도 같은 결과
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItemReservationCleanupScheduler {

  private final ItemReservationService itemReservationService;

  @Scheduled(cron = "0 * * * * *", zone = ServiceTimeZone.KST_ZONE_ID_NAME)
  public void releaseReservationsWithoutActiveTrade() {
    try {
      itemReservationService.releaseReservationsWithoutActiveTrade();
    } catch (RuntimeException cleanupException) {
      log.warn("[item reservation cleanup] failed : {}", cleanupException.getMessage());
    }
  }
}
