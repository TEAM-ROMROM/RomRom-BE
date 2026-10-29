package com.romrom.application.service;

import com.romrom.common.constant.SanctionType;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.mongo.SanctionHistory;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 관리자 > 회원 단건 알림 발송: 대상 검증, 입력 검증, 발송, 감사 이력
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMemberNotificationService {

  private final SanctionHistoryRepository sanctionHistoryRepository;
  private final NotificationService notificationService;
  private final AdminMemberSanctionService adminMemberSanctionService;

  /**
   * 회원에게 알림 발송 (관리자 → 회원 단건)
   */
  @Transactional
  public void sendNotificationToMember(UUID targetMemberId, String title, String content, String notificationTypeName, UUID executorAdminId) {
    adminMemberSanctionService.validateAdminAction(targetMemberId, executorAdminId);

    if (!StringUtils.hasText(title) || title.length() > 100) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    if (!StringUtils.hasText(content) || content.length() > 1000) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    NotificationType resolvedNotificationType;
    try {
      resolvedNotificationType = notificationTypeName != null
          ? NotificationType.valueOf(notificationTypeName)
          : NotificationType.ANNOUNCEMENT;
    } catch (IllegalArgumentException invalidTypeException) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    LocalDateTime publishedAt = LocalDateTime.now();
    Map<String, String> notificationPayload = new HashMap<>();
    notificationPayload.put("notificationType", resolvedNotificationType.name());
    notificationPayload.put("publishedAt", publishedAt.toString());

    try {
      notificationService.sendToMember(targetMemberId, title, content, notificationPayload);
    } catch (Exception sendNotificationException) {
      log.warn("관리자 알림 발송 실패: targetMemberId={}, error={}", targetMemberId, sendNotificationException.getMessage());
    }

    // 감사 로그
    SanctionHistory notificationAuditSanction = SanctionHistory.builder()
        .memberId(targetMemberId)
        .suspendReason("[ADMIN_NOTIFICATION] title=" + title)
        .suspendedAt(LocalDateTime.now(ZoneOffset.UTC))
        .sanctionType(SanctionType.ADMIN_NOTIFICATION)
        .executorAdminId(executorAdminId)
        .build();
    sanctionHistoryRepository.save(notificationAuditSanction);

    log.info("관리자 알림 발송 완료: targetMemberId={}, executorAdminId={}, type={}",
        targetMemberId, executorAdminId, resolvedNotificationType);
  }
}
