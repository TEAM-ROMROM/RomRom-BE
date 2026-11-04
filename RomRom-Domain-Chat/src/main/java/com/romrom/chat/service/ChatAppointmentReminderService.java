package com.romrom.chat.service;

import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.notification.event.ChatAppointmentReminderEvent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 약속 리마인드: 약속 1시간 전 진입 건 선점 > 열린 방 확인 > 양쪽 회원(나간 회원 제외)에게 푸시
 * 방별 알림 끄기는 무시, 전역 채팅 알림 동의는 NotificationService 가 확인
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatAppointmentReminderService {

  // 약속 시각 기준 리마인드 시작 시점 (1시간 전)
  public static final Duration REMINDER_LEAD_DURATION = Duration.ofHours(1);
  // 1회 실행 최대 처리 건수 (몰림 시 다음 실행으로 넘김)
  private static final int MAXIMUM_REMINDERS_PER_RUN = 200;

  private final ChatMessageRepository chatMessageRepository;
  private final ChatUserStateRepository chatUserStateRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 리마인드 대상 반복 선점·발행 > 처리한 약속 수 (이벤트는 커밋 후 비동기 발송)
   */
  @Transactional
  public int sendDueReminders() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime remindUntil = now.plus(REMINDER_LEAD_DURATION);
    int remindedAppointmentCount = 0;
    while (remindedAppointmentCount < MAXIMUM_REMINDERS_PER_RUN) {
      ChatMessage claimedMessage = chatMessageRepository.claimDueAppointmentReminder(now, remindUntil);
      if (claimedMessage == null) {
        break;
      }
      remindedAppointmentCount++;
      try {
        publishReminders(claimedMessage);
      } catch (RuntimeException reminderException) {
        // 건별 실패는 기록 후 계속 (선점 기록이 남아 재발송 없음)
        log.error("[appointment reminder] failed : chatMessageId={}, error={}", claimedMessage.getChatMessageId(),
            reminderException.getMessage(), reminderException);
      }
    }
    return remindedAppointmentCount;
  }

  /**
   * 1건 발행: 종료된 방 제외 > 회원별(보낸 사람·받는 사람) 나간 회원 제외 > 리마인드 이벤트
   */
  private void publishReminders(ChatMessage appointmentMessage) {
    UUID chatRoomId = appointmentMessage.getChatRoomId();
    ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
    if (chatRoom == null || chatRoom.getDeletedAt() != null) {
      log.debug("[appointment reminder] skipped : closed room, chatRoomId={}", chatRoomId);
      return;
    }
    ChatAppointment appointment = appointmentMessage.getAppointment();
    String appointmentTimeLabel = ChatAppointmentContentFormatter.toTimeLabel(appointment.getAppointmentAt());
    for (UUID memberId : List.of(appointmentMessage.getSenderId(), appointmentMessage.getRecipientId())) {
      ChatUserState memberState = chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoomId, memberId).orElse(null);
      if (memberState == null || memberState.isDeleted()) {
        continue;
      }
      eventPublisher.publishEvent(new ChatAppointmentReminderEvent(memberId, chatRoomId,
          appointmentMessage.getChatMessageId(), appointmentTimeLabel, appointment.getPlaceName()));
    }
  }
}
