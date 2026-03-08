package com.romrom.chat.service;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessageRequest;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.service.MemberBlockService;
import com.romrom.notification.event.ChatMessageReceivedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
  private final SimpMessagingTemplate template;
  private final ChatRoutingProperties chatRoutingProperties;
  private final MemberBlockService memberBlockService;
  private final ChatUserStateRepository chatUserStateRepository;
  private final ApplicationEventPublisher eventPublisher;

  // 메시지 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse findRecentMessages(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    // 페이징 및 최신순 정렬 설정
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );
    // 메시지 조회
    Slice<ChatMessage> messageSlice = chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(room.getChatRoomId(), pageable);

    if(room.getTradeReceiver().getMemberId().equals(request.getMember().getMemberId())) {
      if (opponentDeleted(room.getChatRoomId(), room.getTradeSender().getMemberId())) {      // 상대방이 나간 경우
        return ChatRoomResponse.builder()
            .isOpponentDeleted(true)
            .chatRoom(room)
            .messages(messageSlice)
            .build();
      }
      room.getTradeSender().setOnlineIfActiveWithin90Seconds();
    }
    else {
      if (opponentDeleted(room.getChatRoomId(), room.getTradeReceiver().getMemberId())) {      // 상대방이 나간 경우
        return ChatRoomResponse.builder()
            .isOpponentDeleted(true)
            .chatRoom(room)
            .messages(messageSlice)
            .build();
      }
      room.getTradeReceiver().setOnlineIfActiveWithin90Seconds();
    }
    return ChatRoomResponse.builder()
        .isOpponentDeleted(false)
        .messages(messageSlice)
        .chatRoom(room)
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
    memberBlockService.verifyNotBlocked(senderId, recipientId);

    if(request.getType().equals(MessageType.SYSTEM)) {
      log.debug("시스템 메시지 요청은 현재 지원되지 않습니다.");
      return;
    }

    // 이미지 메시지인 경우, 내용이 비어있다면 기본 메시지 설정
    if(request.getType().equals(MessageType.IMAGE) && request.getContent().isBlank()) {
      request.setContent("사진을 보냈습니다.");
    }
    // 메시지 저장
    ChatMessage message = chatMessageRepository.save(ChatMessage.fromChatMessageRequest(request, senderId, recipientId));
    log.debug("채팅 메시지 저장 완료. messageId: {}", message.getChatMessageId());

    // 트랜잭션이 성공적으로 DB에 반영된 후에만 브로커와 FCM 실행
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        // 메시지 브로커 전송
        sendToBroker(message);

        // 수신자가 채팅방 밖에 있는 경우 FCM 푸시 알림 발송
        if (!opponentState.isPresent()) {
          eventPublisher.publishEvent(new ChatMessageReceivedEvent(recipientId, chatRoom.getChatRoomId(), message.getContent()));
          log.debug("채팅 메시지 FCM 알림 이벤트 발행. recipientId: {}, chatRoomId: {}", recipientId, chatRoom.getChatRoomId());
        }
      }
    });
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

    // 실시간 브로커 전송 TODO : 프론트 로직 참고해서 상대방이 채팅방을 볼때만 전송할지 고려
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        sendToBroker(systemMsg);
      }
    });
  }

  // --- Private Helper Method ---

  private void sendToBroker(ChatMessage message) {
    // 메시지 브로커 전송
    ChatMessagePayload payload = ChatMessagePayload.from(message);
    String roomRoutingKey = "chat.room." + payload.getChatRoomId();
    String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;

    // RabbitMQ 브로커에게 메시지 전달
    template.convertAndSend(destination, payload);
    log.debug("채팅 메시지 브로커 송출 완료, destination: {}", destination);
  }

  private boolean opponentDeleted(UUID roomId, UUID opponentId) {
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberId(roomId, opponentId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));
    return opponentState.isDeleted();
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