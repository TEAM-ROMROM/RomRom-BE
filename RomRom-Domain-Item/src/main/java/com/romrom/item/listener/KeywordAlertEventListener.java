package com.romrom.item.listener;

import com.romrom.item.event.ItemRegisteredEvent;
import com.romrom.item.service.KeywordAlertMatchingService;
import com.romrom.item.service.KeywordAlertTargetSelector.KeywordAlertTarget;
import com.romrom.notification.event.KeywordItemRegisteredEvent;
import com.romrom.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 새 물품 키워드 알림 발송 (#873): 등록 커밋 후 비동기 > 대상 조회 > 회원별 알림 1건
 * 매칭·발송 실패는 로그만 (물품 등록 결과 영향 없음, 회원별 실패 격리)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeywordAlertEventListener {

  private final KeywordAlertMatchingService keywordAlertMatchingService;
  private final NotificationService notificationService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleItemRegistered(ItemRegisteredEvent registeredItem) {
    List<KeywordAlertTarget> keywordAlertTargets;
    try {
      keywordAlertTargets = keywordAlertMatchingService.findKeywordAlertTargets(registeredItem);
    } catch (Exception matchingException) {
      log.error("[keyword alert] matching failed : itemId={}, error={}",
          registeredItem.itemId(), matchingException.getMessage(), matchingException);
      return;
    }
    if (keywordAlertTargets.isEmpty()) {
      return;
    }
    log.info("[keyword alert] matched : itemId={}, targetMemberCount={}", registeredItem.itemId(), keywordAlertTargets.size());

    // 회원별 발송 (알림함 기록 > 방해금지 확인 > 푸시, sendToMember 공통 흐름)
    for (KeywordAlertTarget keywordAlertTarget : keywordAlertTargets) {
      try {
        KeywordItemRegisteredEvent keywordNotification = new KeywordItemRegisteredEvent(
            keywordAlertTarget.memberId(), registeredItem.itemId(), registeredItem.itemName(), keywordAlertTarget.keyword());
        notificationService.sendToMember(keywordNotification.getTargetMemberId(), keywordNotification.getTitle(),
            keywordNotification.getBody(), keywordNotification.getPayload());
      } catch (Exception sendException) {
        log.error("[keyword alert] send failed : itemId={}, memberId={}, error={}",
            registeredItem.itemId(), keywordAlertTarget.memberId(), sendException.getMessage(), sendException);
      }
    }
  }
}
