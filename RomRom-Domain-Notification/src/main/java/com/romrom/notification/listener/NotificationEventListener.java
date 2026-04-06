package com.romrom.notification.listener;

import com.romrom.notification.event.AnnouncementEvent;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import com.romrom.notification.event.ItemDeletedByAdminEvent;
import com.romrom.notification.event.ItemLikedEvent;
import com.romrom.notification.event.TradeRequestReceivedEvent;
import com.romrom.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationService notificationService;

  /**
   * 거래 요청 수신 알림
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleTradeRequestReceived(TradeRequestReceivedEvent event) {
    try {
      log.debug("거래 요청 알림 발송: itemName: {}", event.getItemName());
      notificationService.sendToMember(event.getTargetMemberId(), event.getTitle(), event.getBody(), event.getPayload());
    } catch (Exception e) {
      // 알림 발송 실패 시 로깅만 진행
      log.error("거래 요청 알림 발송 실패: itemName: {}, 에러: {}", event.getItemName(), e.getMessage(), e);
    }
  }

  /**
   * 물품 좋아요 알림
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleItemLiked(ItemLikedEvent event) {
    try {
      log.debug("좋아요 알림 발생: itemName: {}", event.getItemName());
      notificationService.sendToMember(event.getTargetMemberId(), event.getTitle(), event.getBody(), event.getPayload());
    } catch (Exception e) {
      // 알림 발송 실패 시 로깅만 진행
      log.error("좋아요 알림 발송 실패: itemName: {}, 에러: {}", event.getItemName(), e.getMessage(), e);
    }
  }

  /**
   * 채팅 메시지 수신 알림
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
    try {
      log.debug("채팅 메시지 알림 발송: chatRoomId: {}", event.getChatRoomId());
      notificationService.sendToMember(event.getTargetMemberId(), event.getTitle(), event.getBody(), event.getPayload());
    } catch (Exception e) {
      // 알림 발송 실패 시 로깅만 진행
      log.error("채팅 메시지 알림 발송 실패: chatRoomId: {}, 에러: {}", event.getChatRoomId(), e.getMessage(), e);
    }
  }

  /**
   * 공지사항 전체 사용자 알림 (롬롬 소식 알림 동의한 멤버에게만 FCM 발송, notification_history 저장 없음)
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleAnnouncementCreated(AnnouncementEvent event) {
    try {
      log.debug("공지사항 전체 알림 발송: announcementId: {}", event.getAnnouncementId());
      notificationService.sendAnnouncement(event.getTitle(), event.getBody(), event.getPayload());
    } catch (Exception e) {
      log.error("공지사항 알림 발송 실패: announcementId: {}, 에러: {}", event.getAnnouncementId(), e.getMessage(), e);
    }
  }

  /**
   * 관리자에 의해 물품 삭제 알림 (영향받는 거래 상대방 대상)
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleItemDeletedByAdmin(ItemDeletedByAdminEvent event) {
    try {
      log.debug("관리자 물품 삭제 알림 발송: deletedItemName={}, targetMemberCount={}",
          event.getDeletedItemName(), event.getAffectedMemberIds().size());
      notificationService.sendToMembers(
          event.getAffectedMemberIds(),
          event.getTitle(),
          event.getBody(),
          event.getPayload()
      );
    } catch (Exception e) {
      log.error("관리자 물품 삭제 알림 발송 실패: deletedItemName={}, 에러: {}",
          event.getDeletedItemName(), e.getMessage(), e);
    }
  }
}
