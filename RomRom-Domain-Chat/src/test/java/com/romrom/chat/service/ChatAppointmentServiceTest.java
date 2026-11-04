package com.romrom.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 약속 카드: 제안 입력 검증, 수락 권한·경합, 취소 후 송출·상대 푸시(방 알림 끄기 반영)
 */
@ExtendWith(MockitoExtension.class)
class ChatAppointmentServiceTest {

  @Mock private ChatMessageService chatMessageService;
  @Mock private ChatMessageRepository chatMessageRepository;
  @Mock private ChatUserStateRepository chatUserStateRepository;
  @Mock private ChatWebSocketService chatWebSocketService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ChatAppointmentService chatAppointmentService;

  private final UUID chatRoomId = UUID.randomUUID();
  private final Member proposer = Member.builder().memberId(UUID.randomUUID()).nickname("제안자").build();
  private final Member opponent = Member.builder().memberId(UUID.randomUUID()).nickname("상대").build();
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

  @BeforeEach
  void setUp() {
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void tearDown() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private ChatRoomRequest createProposalRequest(LocalDateTime appointmentAt, String placeName, Double latitude,
                                                Double longitude) {
    return ChatRoomRequest.builder().chatRoomId(chatRoomId).appointmentAt(appointmentAt).placeName(placeName)
        .latitude(latitude).longitude(longitude).build();
  }

  private ChatMessage givenAppointmentMessage() {
    ChatMessage appointmentMessage = ChatMessage.builder()
        .chatMessageId("appointment-1")
        .chatRoomId(chatRoomId)
        .senderId(proposer.getMemberId())
        .recipientId(opponent.getMemberId())
        .type(MessageType.TEXT)
        .appointment(ChatAppointment.propose(LocalDateTime.now().plusDays(1), "군자역", null, null,
            proposer.getMemberId()))
        .build();
    when(chatMessageService.findMessageInChatRoom(chatRoomId, "appointment-1")).thenReturn(appointmentMessage);
    return appointmentMessage;
  }

  private ChatRoomRequest createActionRequest(Member member) {
    return ChatRoomRequest.builder().member(member).chatRoomId(chatRoomId).chatMessageId("appointment-1").build();
  }

  private ErrorCode proposalErrorOf(ChatRoomRequest request) {
    return Assertions.assertThrows(CustomException.class,
        () -> ChatAppointmentService.validateProposal(request, now)).getErrorCode();
  }

  @Test
  @DisplayName("제안 검증: 과거·90일 초과·빈 장소·51자 장소·좌표 한쪽만 > INVALID_CHAT_APPOINTMENT")
  void 제안_입력_검증() {
    Assertions.assertEquals(ErrorCode.INVALID_CHAT_APPOINTMENT,
        proposalErrorOf(createProposalRequest(now.minusMinutes(1), "군자역", null, null)));
    Assertions.assertEquals(ErrorCode.INVALID_CHAT_APPOINTMENT,
        proposalErrorOf(createProposalRequest(now.plusDays(91), "군자역", null, null)));
    Assertions.assertEquals(ErrorCode.INVALID_CHAT_APPOINTMENT,
        proposalErrorOf(createProposalRequest(now.plusHours(3), "  ", null, null)));
    Assertions.assertEquals(ErrorCode.INVALID_CHAT_APPOINTMENT,
        proposalErrorOf(createProposalRequest(now.plusHours(3), "가".repeat(51), null, null)));
    Assertions.assertEquals(ErrorCode.INVALID_CHAT_APPOINTMENT,
        proposalErrorOf(createProposalRequest(now.plusHours(3), "군자역", 37.5, null)));
    Assertions.assertDoesNotThrow(() -> ChatAppointmentService.validateProposal(
        createProposalRequest(now.plusHours(3), "군자역", 37.5571, 127.0794), now));
  }

  @Test
  @DisplayName("제안한 사람이 수락 > CHAT_APPOINTMENT_ACCEPT_FORBIDDEN")
  void 제안자_수락_거부() {
    givenAppointmentMessage();

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> chatAppointmentService.acceptAppointment(createActionRequest(proposer)));

    Assertions.assertEquals(ErrorCode.CHAT_APPOINTMENT_ACCEPT_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("이미 상태가 바뀐 약속 수락(조건부 갱신 실패) > CHAT_APPOINTMENT_STATUS_CONFLICT")
  void 수락_경합_충돌() {
    givenAppointmentMessage();
    when(chatMessageRepository.changeAppointmentStatusIfCurrent(eq("appointment-1"), any(),
        eq(ChatAppointmentStatus.ACCEPTED), eq(opponent.getMemberId()), anyString())).thenReturn(null);

    CustomException exception = Assertions.assertThrows(CustomException.class,
        () -> chatAppointmentService.acceptAppointment(createActionRequest(opponent)));

    Assertions.assertEquals(ErrorCode.CHAT_APPOINTMENT_STATUS_CONFLICT, exception.getErrorCode());
  }

  @Test
  @DisplayName("취소 성공 > 커밋 후 변경 토픽 송출 + 방 밖 상대에게 채팅 푸시")
  void 취소_송출_푸시() {
    ChatMessage appointmentMessage = givenAppointmentMessage();
    when(chatMessageRepository.changeAppointmentStatusIfCurrent(eq("appointment-1"), any(),
        eq(ChatAppointmentStatus.CANCELED), eq(proposer.getMemberId()), anyString())).thenReturn(appointmentMessage);
    ChatUserState absentOpponentState = ChatUserState.create(chatRoomId, opponent.getMemberId());
    when(chatUserStateRepository.findByChatRoomIdAndMemberIdNot(chatRoomId, proposer.getMemberId()))
        .thenReturn(Optional.of(absentOpponentState));

    chatAppointmentService.cancelAppointment(createActionRequest(proposer));
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

    verify(chatWebSocketService).sendMessageUpdateEvent(appointmentMessage);
    verify(eventPublisher).publishEvent(any(ChatMessageReceivedEvent.class));
  }

  @Test
  @DisplayName("상대가 방 알림을 끈 경우 상태 변경 푸시 생략")
  void 알림_끈_상대_푸시_생략() {
    ChatMessage appointmentMessage = givenAppointmentMessage();
    when(chatMessageRepository.changeAppointmentStatusIfCurrent(eq("appointment-1"), any(),
        eq(ChatAppointmentStatus.CANCELED), eq(proposer.getMemberId()), anyString())).thenReturn(appointmentMessage);
    ChatUserState mutedOpponentState = ChatUserState.builder().chatRoomId(chatRoomId).memberId(opponent.getMemberId())
        .leftAt(LocalDateTime.now()).notificationMutedAt(LocalDateTime.now()).build();
    when(chatUserStateRepository.findByChatRoomIdAndMemberIdNot(chatRoomId, proposer.getMemberId()))
        .thenReturn(Optional.of(mutedOpponentState));

    chatAppointmentService.cancelAppointment(createActionRequest(proposer));

    verify(eventPublisher, never()).publishEvent(any(ChatMessageReceivedEvent.class));
  }
}
