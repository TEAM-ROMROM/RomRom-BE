package com.romrom.notification.service;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.notification.dto.NotificationHistoryRequest;
import com.romrom.notification.dto.NotificationHistoryResponse;
import com.romrom.notification.entity.NotificationHistory;
import com.romrom.notification.repository.NotificationHistoryRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationHistoryService {

  private final NotificationHistoryRepository notificationHistoryRepository;

  /**
   * 알림 히스토리 저장
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationHistory saveNotificationHistory(NotificationHistoryRequest request) {
    NotificationHistory notificationHistory = NotificationHistory.builder()
      .member(request.getMember())
      .notificationType(request.getNotificationType())
      .title(request.getTitle())
      .body(request.getBody())
      .payload(request.getPayload())
      .isRead(false)
      .publishedAt(request.getPublishedAt())
      .build();

    NotificationHistory saved = notificationHistoryRepository.save(notificationHistory);
    log.debug("알림 히스토리 저장 완료: notificationHistoryId={}, memberId={}, notificationType={}",
      saved.getNotificationHistoryId(), request.getMember().getMemberId(), request.getNotificationType());

    return saved;
  }

  /**
   * 사용자별 알림 목록 조회
   */
  @Transactional(readOnly = true)
  public NotificationHistoryResponse getNotificationHistoryPage(NotificationHistoryRequest request) {
    log.debug("사용자: {} 의 알림 목록 조회", request.getMember().getMemberId());

    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    Page<NotificationHistory> notificationHistoryPage = notificationHistoryRepository
      .findByMemberOrderByPublishedAtDesc(request.getMember(), pageable);

    return NotificationHistoryResponse.builder()
      .notificationHistoryPage(notificationHistoryPage)
      .build();
  }

  /**
   * 안읽은 알림 개수 조회
   */
  @Transactional(readOnly = true)
  public NotificationHistoryResponse getUnReadNotificationCount(NotificationHistoryRequest request) {
    Long unReadCount = notificationHistoryRepository
      .countByMemberAndIsRead(request.getMember(), false);
    log.debug("사용자: {} 의 안읽은 알림 개수: {}", request.getMember().getMemberId(), unReadCount);
    return NotificationHistoryResponse.builder()
      .unReadCount(unReadCount)
      .build();
  }

  /**
   * 알림 읽음 처리
   */
  @Transactional
  public void markAsRead(NotificationHistoryRequest request) {
    NotificationHistory notificationHistory = findNotificationHistoryById(request.getNotificationHistoryId());

    validateReceiver(notificationHistory, request.getMember());

    notificationHistory.setIsRead(true);
    log.debug("알림: {} 읽음 처리", notificationHistory.getNotificationHistoryId());
  }

  /**
   * 모든 알림 읽음 처리
   */
  @Transactional
  public void markAllAsRead(NotificationHistoryRequest request) {
    int updatedCount = notificationHistoryRepository.markAllAsReadByMemberId(request.getMember().getMemberId());
    log.debug("사용자: {} 의 모든 알림 읽음 처리. 처리 개수: {} 개", request.getMember().getMemberId(), updatedCount);
  }

  /**
   * 알림 삭제 (Hard Delete)
   */
  @Transactional
  public void deleteNotification(NotificationHistoryRequest request) {
    NotificationHistory notificationHistory = findNotificationHistoryById(request.getNotificationHistoryId());

    validateReceiver(notificationHistory, request.getMember());

    notificationHistoryRepository.delete(notificationHistory);
    log.debug("알림 삭제 완료: notificationHistoryId={}", notificationHistory.getNotificationHistoryId());
  }

  /**
   * 전체 알림 삭제 (Hard Delete)
   */
  @Transactional
  public void deleteAllNotifications(NotificationHistoryRequest request) {
    notificationHistoryRepository.deleteAllByMember(request.getMember());
    log.debug("사용자: {} 의 모든 알림 삭제 완료", request.getMember().getMemberId());
  }

  // Id 기반 알림 조회
  private NotificationHistory findNotificationHistoryById(UUID notificationHistoryId) {
    return notificationHistoryRepository.findById(notificationHistoryId)
      .orElseThrow(() -> {
        log.error("UUID: {}에 해당하는 알림을 찾을 수 없습니다", notificationHistoryId);
        return new CustomException(ErrorCode.NOTIFICATION_HISTORY_NOT_FOUND);
      });
  }

  // 알림 수신자 (소유자) 검증
  private void validateReceiver(NotificationHistory notificationHistory, Member member) {
    if (!notificationHistory.getMember().getMemberId().equals(member.getMemberId())) {
      log.error("알림의 수신자(소유자)만 접근 가능합니다");
      throw new CustomException(ErrorCode.INVALID_NOTIFICATION_HISTORY_OWNER);
    }
  }
}
