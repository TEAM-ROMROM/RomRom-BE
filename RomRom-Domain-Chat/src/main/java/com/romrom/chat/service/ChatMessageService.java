package com.romrom.chat.service;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.event.ChatRecommendationRequestedEvent;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.UgcFilterService;
import com.romrom.member.entity.Member;
import com.romrom.member.service.MemberBlockService;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final MemberBlockService memberBlockService;
  private final ChatUserStateRepository chatUserStateRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final UgcFilterService ugcFilterService;
  private final ChatActionRecommendationService chatActionRecommendationService;

  // 메시지 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse findRecentMessages(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    ChatRoom room = validateChatRoomMember(memberId, request.getChatRoomId());
    // 페이징 및 최신순 정렬 설정
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );
    // 메시지 조회
    Slice<ChatMessage> messageSlice = chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(room.getChatRoomId(), pageable);

    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(room.getChatRoomId(), memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    // 상대방이 나간 경우
    if(opponentState.isDeleted()) {
      return ChatRoomResponse.builder()
          .isOpponentDeleted(true)
          .chatRoom(room)
          .messages(messageSlice)
          .build();
    }

    if(room.getTradeReceiver().getMemberId().equals(memberId)) {
      room.getTradeSender().setOnlineIfActiveWithin90Seconds();
    }
    else {
      room.getTradeReceiver().setOnlineIfActiveWithin90Seconds();
    }

    return ChatRoomResponse.builder()
        .isOpponentDeleted(false)
        .messages(messageSlice)
        .chatRoom(room)
        .opponentState(opponentState)
        .latestRecommendation(chatActionRecommendationService.recommendForViewer(
            room,
            memberId,
            request.getPageNumber() == 0 ? messageSlice.getContent() : null
        ))
        .build();
  }

  // 메시지 저장
  @Transactional
  public void saveAndSendMessage(ChatMessageRequest request, CustomUserDetails customUserDetails) {
    UUID senderId = customUserDetails.getMember().getMemberId();

    // 채팅방 존재 및 멤버 확인
    ChatRoom chatRoom = validateChatRoomMember(senderId, request.getChatRoomId());
    UUID recipientId;
    // 수신자 설정
    if (!chatRoom.getTradeReceiver().getMemberId().equals(senderId)) {
      recipientId = chatRoom.getTradeReceiver().getMemberId();
    }
    else {
      recipientId = chatRoom.getTradeSender().getMemberId();
    }
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoom.getChatRoomId(), recipientId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));
    if(opponentState.isDeleted()) {
      log.debug("상대방이 채팅방을 삭제한 상태, 즉 거래요청이 취소/거래완료 상태이므로 메시지 전송 불가. recipientId: {}, chatRoomId: {}", recipientId, chatRoom.getChatRoomId());
      throw new CustomException(ErrorCode.CANNOT_SEND_MESSAGE_TO_DELETED_CHATROOM);
    }

    // 연결된 물품이 관리자에 의해 삭제된 경우 메시지 전송 차단
    TradeRequestHistory tradeRequestHistory = chatRoom.getTradeRequestHistory();
    if (Boolean.TRUE.equals(tradeRequestHistory.getGiveItem().getIsDeleted())
        || Boolean.TRUE.equals(tradeRequestHistory.getTakeItem().getIsDeleted())) {
      log.debug("관리자에 의해 삭제된 물품 채팅방 메시지 전송 차단. chatRoomId: {}", chatRoom.getChatRoomId());
      throw new CustomException(ErrorCode.CANNOT_SEND_MESSAGE_TO_ADMIN_DELETED_ITEM_CHATROOM);
    }

    memberBlockService.verifyNotBlocked(senderId, recipientId);

    if (request.getType() == null || !request.getType().isClientSendable()) {
      log.debug("클라이언트 전송 불가 메시지 타입 요청. type={}", request.getType());
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    // TEXT 메시지만 비속어 감지 (차단하지 않고 경고 플래그만 설정)
    final boolean isProfanityDetected = request.getType().equals(MessageType.TEXT)
        && ugcFilterService.containsProhibitedContent(request.getContent());
    if (isProfanityDetected) {
      log.warn("채팅 비속어 감지 (경고): chatRoomId={}, senderId={}", request.getChatRoomId(), senderId);
    }

    // 이미지 메시지인 경우, 내용이 비어있다면 기본 메시지 설정
    if (request.getType().equals(MessageType.LOCATION)) {
      if (request.getLatitude() == null || request.getLongitude() == null) {
        log.error("위치 메시지 전송 오류 : latitude/longitude 누락. chatRoomId={}, senderId={}", request.getChatRoomId(), senderId);
        throw new CustomException(ErrorCode.INVALID_REQUEST);
      }
      if (isBlank(request.getContent())) {
        request.setContent("위치를 보냈습니다.");
      }
    }

    if(request.getType().equals(MessageType.IMAGE) && isBlank(request.getContent())) {
      request.setContent("사진을 보냈습니다.");
    }
    // 메시지 저장
    ChatMessage message = chatMessageRepository.save(ChatMessage.fromChatMessageRequest(request, senderId, recipientId));
    log.debug("채팅 메시지 저장 완료. messageId: {}", message.getChatMessageId());

    registerMessageDispatch(
        message,
        opponentState,
        chatRoom,
        isProfanityDetected,
        customUserDetails.getMember().getNickname(),
        true,
        true
    );
    publishRecommendationEvent(message, senderId, recipientId, opponentState);
  }

  @Transactional
  public void sendSystemMessage(ChatRoom room, UUID leaverId, ChatUserState opponentState) {
    String content = "상대방이 채팅방을 나갔습니다. 더 이상 메시지를 보낼 수 없습니다.";

    // 상대방이 나중에 들어와도 볼 수 있게 DB 저장
    ChatMessage systemMsg = ChatMessage.builder()
        .chatRoomId(room.getChatRoomId())
        .senderId(leaverId)               // 혹은 시스템 전용 ID
        .recipientId(opponentState.getMemberId())
        .content(content)
        .type(MessageType.SYSTEM)
        .build();
    chatMessageRepository.save(systemMsg);

    registerMessageDispatch(systemMsg, opponentState, room, false, null, false, true);
  }

  @Transactional
  public void sendTradeSystemMessage(ChatRoom room, UUID senderId, UUID recipientId, MessageType type, String content) {
    if (!type.isTradeCompletionType()) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberId(room.getChatRoomId(), recipientId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    Member sender = room.getTradeReceiver().getMemberId().equals(senderId)
        ? room.getTradeReceiver()
        : room.getTradeSender();

    ChatMessage systemMessage = ChatMessage.builder()
        .chatRoomId(room.getChatRoomId())
        .senderId(senderId)
        .recipientId(recipientId)
        .content(content)
        .type(type)
        .build();
    chatMessageRepository.save(systemMessage);

    registerMessageDispatch(systemMessage, opponentState, room, false, sender.getNickname(), true, false);
    publishRecommendationEvent(systemMessage, senderId, recipientId, opponentState);
  }

  // --- Private Helper Method ---

  private void registerMessageDispatch(ChatMessage message,
                                       ChatUserState opponentState,
                                       ChatRoom chatRoom,
                                       boolean isProfanityDetected,
                                       String senderNickname,
                                       boolean notifyWhenOpponentAbsent,
                                       boolean shouldBroadcastWhenOpponentAbsent) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        boolean opponentPresent = !opponentState.isDeleted() && opponentState.isPresent();

        if (opponentPresent || shouldBroadcastWhenOpponentAbsent) {
          chatWebSocketService.sendToBroker(message, isProfanityDetected);
        }

        if (opponentPresent) {
          chatWebSocketService.sendReadEvent(opponentState);
          return;
        }

        // 알림 안보내는 경우는 채팅방 나간 경우
        if (!notifyWhenOpponentAbsent || senderNickname == null) {
          return;
        }

        eventPublisher.publishEvent(new ChatMessageReceivedEvent(
            opponentState.getMemberId(),
            chatRoom.getChatRoomId(),
            senderNickname,
            message.getContent()
        ));
        log.debug("채팅 메시지 FCM 알림 이벤트 발행. recipientId: {}, chatRoomId: {}", opponentState.getMemberId(), chatRoom.getChatRoomId());
      }
    });
  }

  // 일반 채팅 + 교환 관련 요청 채팅 시 발행
  private void publishRecommendationEvent(ChatMessage message,
                                          UUID senderId,
                                          UUID recipientId,
                                          ChatUserState opponentState) {
    if (!(message.getType().isClientSendable() || message.getType().isTradeCompletionType())) {
      return;
    }

    eventPublisher.publishEvent(new ChatRecommendationRequestedEvent(
        message.getChatRoomId(),
        message.getChatMessageId(),
        senderId,
        recipientId,
        !opponentState.isDeleted() && opponentState.isPresent()
    ));
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private ChatRoom validateChatRoomMember(UUID memberId, UUID chatRoomId) {
    ChatRoom chatRoom = chatRoomRepository.findByChatRoomIdWithSenderAndReceiver(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    if (!chatRoom.isMember(memberId)) {
      log.error("채팅방 회원 검증 오류 : 요청자는 채팅방 멤버가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_CHATROOM_MEMBER);
    }
    return chatRoom;
  }
}
