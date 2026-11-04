package com.romrom.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.notification.event.ChatAppointmentReminderEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 약속 리마인드: 선점 건 > 나간 회원 제외 발송, 종료된 방 제외
 */
@ExtendWith(MockitoExtension.class)
class ChatAppointmentReminderServiceTest {

  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatUserStateRepository chatUserStateRepository;
  @Mock private ChatRoomRepository chatRoomRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ChatAppointmentReminderService chatAppointmentReminderService;

  private final UUID chatRoomId = UUID.randomUUID();
  private final UUID senderId = UUID.randomUUID();
  private final UUID recipientId = UUID.randomUUID();

  private void givenOneClaimedAppointment() {
    ChatAppointment appointment = ChatAppointment.propose(LocalDateTime.now().plusMinutes(50), "군자역", null, null,
        senderId);
    appointment.changeStatus(ChatAppointmentStatus.ACCEPTED, recipientId);
    ChatMessage appointmentMessage = ChatMessage.builder().chatMessageId("appointment-1").chatRoomId(chatRoomId)
        .senderId(senderId).recipientId(recipientId).type(MessageType.TEXT).appointment(appointment).build();
    when(chatMessageRepository.claimDueAppointmentReminder(any(), any())).thenReturn(appointmentMessage, (ChatMessage) null);
  }

  @Test
  @DisplayName("열린 방: 나간 회원 제외 1명에게만 리마인드")
  void 나간_회원_제외_발송() {
    givenOneClaimedAppointment();
    when(chatRoomRepository.findById(chatRoomId)).thenReturn(Optional.of(ChatRoom.builder().chatRoomId(chatRoomId).build()));
    ChatUserState removedSenderState = ChatUserState.create(chatRoomId, senderId);
    removedSenderState.removeRoom();
    when(chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoomId, senderId)).thenReturn(Optional.of(removedSenderState));
    when(chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoomId, recipientId))
        .thenReturn(Optional.of(ChatUserState.create(chatRoomId, recipientId)));

    int remindedAppointmentCount = chatAppointmentReminderService.sendDueReminders();

    Assertions.assertEquals(1, remindedAppointmentCount);
    verify(eventPublisher, times(1)).publishEvent(any(ChatAppointmentReminderEvent.class));
  }

  @Test
  @DisplayName("종료된 방(deletedAt) 약속은 리마인드 없음")
  void 종료된_방_제외() {
    givenOneClaimedAppointment();
    when(chatRoomRepository.findById(chatRoomId))
        .thenReturn(Optional.of(ChatRoom.builder().chatRoomId(chatRoomId).deletedAt(LocalDateTime.now()).build()));

    chatAppointmentReminderService.sendDueReminders();

    verify(eventPublisher, never()).publishEvent(any(ChatAppointmentReminderEvent.class));
  }
}
