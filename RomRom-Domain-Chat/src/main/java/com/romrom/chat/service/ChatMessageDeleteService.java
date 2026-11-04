package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 내 메시지 삭제: 보낸 사람·24시간 이내·일반 메시지만 soft delete > 변경 토픽 송출 (원본은 관리자 확인용 보존)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageDeleteService {

  // 보낸 뒤 삭제 가능 시간 (24시간)
  public static final Duration DELETABLE_DURATION = Duration.ofHours(24);

  private final ChatMessageService chatMessageService;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;

  /**
   * 메시지 삭제: 멤버 확인 > 메시지 조회 > 삭제 가능 검증 > 삭제 표시 저장 > 커밋 후 변경본 송출
   */
  @Transactional
  public ChatRoomResponse deleteMyMessage(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    chatMessageService.validateChatRoomMember(memberId, request.getChatRoomId());
    ChatMessage chatMessage = chatMessageService.findMessageInChatRoom(request.getChatRoomId(), request.getChatMessageId());

    validateDeletable(chatMessage, memberId, LocalDateTime.now());
    chatMessage.markDeleted();
    chatMessageRepository.save(chatMessage);
    log.debug("[chat delete] deleted : chatRoomId={}, chatMessageId={}", chatMessage.getChatRoomId(),
        chatMessage.getChatMessageId());

    // 저장 후 회원 조회용 마스킹 (이 인스턴스는 다시 저장하지 않음)
    chatMessage.maskForMemberView();
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        chatWebSocketService.sendMessageUpdateEvent(chatMessage);
      }
    });
    return ChatRoomResponse.builder().chatMessage(chatMessage).build();
  }

  /**
   * 삭제 가능 조건 4개: 보낸 사람, 미삭제·미숨김 일반 메시지(TEXT·IMAGE·LOCATION, 약속 제외), 보낸 지 24시간 이내
   */
  static void validateDeletable(ChatMessage chatMessage, UUID memberId, LocalDateTime now) {
    if (!memberId.equals(chatMessage.getSenderId())) {
      throw new CustomException(ErrorCode.CHAT_MESSAGE_DELETE_NOT_SENDER);
    }
    boolean isRegularMessage = chatMessage.getType() != null && chatMessage.getType().isClientSendable();
    if (chatMessage.isDeleted() || chatMessage.isHidden() || chatMessage.hasAppointment() || !isRegularMessage) {
      throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_DELETABLE);
    }
    LocalDateTime sentAt = chatMessage.getCreatedDate();
    if (sentAt == null || sentAt.plus(DELETABLE_DURATION).isBefore(now)) {
      throw new CustomException(ErrorCode.CHAT_MESSAGE_DELETE_PERIOD_EXPIRED);
    }
  }
}
