package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatAppointment;
import com.romrom.chat.entity.mongo.ChatAppointmentStatus;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.service.ChatMessageService.SendableChatRoom;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 약속 카드: 제안(TEXT + appointment 필드로 전송), 수락(제안 받은 사람), 취소(양쪽)
 * 상태 변경 > 본문 글자도 갱신(옛 앱 표시용) > 변경 토픽 송출 > 상대 부재·알림 켜짐이면 채팅 푸시
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatAppointmentService {

  // 약속 제안 가능 기간 (오늘부터 90일)
  static final int MAXIMUM_DAYS_AHEAD = 90;
  private static final Set<ChatAppointmentStatus> ACCEPTABLE_STATUSES = EnumSet.of(ChatAppointmentStatus.PROPOSED);
  private static final Set<ChatAppointmentStatus> CANCELABLE_STATUSES =
      EnumSet.of(ChatAppointmentStatus.PROPOSED, ChatAppointmentStatus.ACCEPTED);
  private static final String ACCEPTED_PUSH_BODY = "약속을 수락했어요";
  private static final String CANCELED_PUSH_BODY = "약속을 취소했어요";

  private final ChatMessageService chatMessageService;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatUserStateRepository chatUserStateRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 약속 제안: 입력 검증 > 전송 가능한 방 확인 > TEXT 메시지(본문 = 약속 글자) 저장·송출
   */
  @Transactional
  public ChatRoomResponse proposeAppointment(ChatRoomRequest request) {
    Member proposer = request.getMember();
    validateProposal(request, LocalDateTime.now());
    SendableChatRoom sendableChatRoom = chatMessageService.validateSendableChatRoom(
        proposer.getMemberId(), request.getChatRoomId());

    ChatAppointment appointment = ChatAppointment.propose(request.getAppointmentAt(), request.getPlaceName().trim(),
        request.getLatitude(), request.getLongitude(), proposer.getMemberId());
    ChatMessage appointmentMessage = ChatMessage.builder()
        .chatRoomId(sendableChatRoom.chatRoom().getChatRoomId())
        .senderId(proposer.getMemberId())
        .recipientId(sendableChatRoom.recipientId())
        .content(ChatAppointmentContentFormatter.toMessageContent(appointment))
        .type(MessageType.TEXT)
        .imageUrls(new ArrayList<>())
        .appointment(appointment)
        .build();
    ChatMessage savedMessage = chatMessageService.saveAndDispatchServerCreatedMessage(
        appointmentMessage, sendableChatRoom, proposer.getNickname());
    log.debug("[chat appointment] proposed : chatRoomId={}, chatMessageId={}", savedMessage.getChatRoomId(),
        savedMessage.getChatMessageId());
    return ChatRoomResponse.builder().chatMessage(savedMessage).build();
  }

  /**
   * 약속 수락: 전송 가능한 방 > 약속 메시지 > 제안 받은 사람·약속 시각 전 확인 > PROPOSED일 때만 ACCEPTED
   */
  @Transactional
  public ChatRoomResponse acceptAppointment(ChatRoomRequest request) {
    Member accepter = request.getMember();
    chatMessageService.validateSendableChatRoom(accepter.getMemberId(), request.getChatRoomId());
    ChatMessage appointmentMessage = findAppointmentMessage(request);

    if (accepter.getMemberId().equals(appointmentMessage.getSenderId())) {
      throw new CustomException(ErrorCode.CHAT_APPOINTMENT_ACCEPT_FORBIDDEN);
    }
    if (!appointmentMessage.getAppointment().getAppointmentAt().isAfter(LocalDateTime.now())) {
      throw new CustomException(ErrorCode.INVALID_CHAT_APPOINTMENT);
    }
    return changeStatus(appointmentMessage, ACCEPTABLE_STATUSES, ChatAppointmentStatus.ACCEPTED, accepter,
        ACCEPTED_PUSH_BODY);
  }

  /**
   * 약속 취소: 멤버 확인 > 약속 메시지 > PROPOSED·ACCEPTED일 때만 CANCELED (양쪽 모두 가능)
   */
  @Transactional
  public ChatRoomResponse cancelAppointment(ChatRoomRequest request) {
    Member canceler = request.getMember();
    chatMessageService.validateChatRoomMember(canceler.getMemberId(), request.getChatRoomId());
    ChatMessage appointmentMessage = findAppointmentMessage(request);
    return changeStatus(appointmentMessage, CANCELABLE_STATUSES, ChatAppointmentStatus.CANCELED, canceler,
        CANCELED_PUSH_BODY);
  }

  /**
   * 제안 입력 검증 3개: 일시(지금 이후, 90일 이내), 장소 이름(1~50자), 좌표(위도·경도 함께, 범위 안)
   */
  static void validateProposal(ChatRoomRequest request, LocalDateTime now) {
    LocalDateTime appointmentAt = request.getAppointmentAt();
    if (request.getChatRoomId() == null || appointmentAt == null
        || !appointmentAt.isAfter(now) || appointmentAt.isAfter(now.plusDays(MAXIMUM_DAYS_AHEAD))) {
      throw new CustomException(ErrorCode.INVALID_CHAT_APPOINTMENT);
    }
    String placeName = request.getPlaceName();
    if (placeName == null || placeName.isBlank() || placeName.trim().length() > ChatAppointment.PLACE_NAME_MAX_LENGTH) {
      throw new CustomException(ErrorCode.INVALID_CHAT_APPOINTMENT);
    }
    Double latitude = request.getLatitude();
    Double longitude = request.getLongitude();
    if ((latitude == null) != (longitude == null)) {
      throw new CustomException(ErrorCode.INVALID_CHAT_APPOINTMENT);
    }
    if (latitude != null && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
      throw new CustomException(ErrorCode.INVALID_CHAT_APPOINTMENT);
    }
  }

  /**
   * 약속 메시지 조회 (약속 없는 메시지 > 잘못된 요청, 관리자 숨김 > 상태 충돌)
   */
  private ChatMessage findAppointmentMessage(ChatRoomRequest request) {
    ChatMessage chatMessage = chatMessageService.findMessageInChatRoom(request.getChatRoomId(), request.getChatMessageId());
    if (!chatMessage.hasAppointment()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    if (chatMessage.isHidden() || chatMessage.isDeleted()) {
      throw new CustomException(ErrorCode.CHAT_APPOINTMENT_STATUS_CONFLICT);
    }
    return chatMessage;
  }

  /**
   * 상태 변경 공통: 조건부 원자적 갱신(경합 시 충돌) > 커밋 후 변경본 송출 > 상대 채팅 푸시
   */
  private ChatRoomResponse changeStatus(ChatMessage appointmentMessage, Set<ChatAppointmentStatus> allowedCurrentStatuses,
                                        ChatAppointmentStatus newStatus, Member changedMember, String pushBody) {
    ChatAppointment changedAppointment = appointmentMessage.getAppointment();
    changedAppointment.changeStatus(newStatus, changedMember.getMemberId());
    String newContent = ChatAppointmentContentFormatter.toMessageContent(changedAppointment);

    ChatMessage updatedMessage = chatMessageRepository.changeAppointmentStatusIfCurrent(
        appointmentMessage.getChatMessageId(), allowedCurrentStatuses, newStatus, changedMember.getMemberId(), newContent);
    if (updatedMessage == null) {
      throw new CustomException(ErrorCode.CHAT_APPOINTMENT_STATUS_CONFLICT);
    }
    log.debug("[chat appointment] status changed : chatMessageId={}, status={}", updatedMessage.getChatMessageId(),
        newStatus);

    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        chatWebSocketService.sendMessageUpdateEvent(updatedMessage);
      }
    });
    publishPushToAbsentOpponent(updatedMessage.getChatRoomId(), changedMember, pushBody);
    return ChatRoomResponse.builder().chatMessage(updatedMessage).build();
  }

  /**
   * 상대가 방 밖이고 이 방 알림 켜짐이면 채팅 푸시 (나간 상대 제외)
   */
  private void publishPushToAbsentOpponent(UUID chatRoomId, Member changedMember, String pushBody) {
    ChatUserState opponentState = chatUserStateRepository
        .findByChatRoomIdAndMemberIdNot(chatRoomId, changedMember.getMemberId())
        .orElse(null);
    if (opponentState == null || opponentState.isDeleted() || opponentState.isPresent()
        || opponentState.isNotificationMuted()) {
      return;
    }
    eventPublisher.publishEvent(new ChatMessageReceivedEvent(
        opponentState.getMemberId(), chatRoomId, changedMember.getNickname(), pushBody));
  }
}
