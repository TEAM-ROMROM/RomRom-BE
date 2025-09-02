package com.romrom.chat.service;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.stomp.properties.ChatRoutingProperties;
import com.romrom.member.entity.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

  private final ChatRoomService chatRoomService;
  private final ChatMessageRepository chatMessageRepository;
  private final SimpMessagingTemplate template;
  private final ChatRoutingProperties chatRoutingProperties;

  // 메시지 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse findRecentMessages(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = chatRoomService.validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    // 페이징 및 최신순 정렬 설정
    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );
    return ChatRoomResponse.builder()
        .messages(chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(room.getChatRoomId(), pageable))
        .chatRoom(room)
        .build();
  }

  // 메시지 저장
  @Transactional
  public void saveMessage(ChatMessagePayload payload, CustomUserDetails customUserDetails) {
    Member sender = customUserDetails.getMember();

    // 채팅방 존재 및 멤버 확인
    ChatRoom chatRoom = chatRoomService.validateChatRoomMember(sender.getMemberId(), payload.getChatRoomId());
    Member recipient = chatRoom.getTradeSender();

    // 메시지 저장
    ChatMessage message = chatMessageRepository.save(ChatMessage.fromPayload(payload, sender, recipient));
    log.debug("채팅 메시지 저장 완료. messageId: {}", message.getChatMessageId());

    // 메시지 브로커 전송
    String roomRoutingKey = "chat.room." + payload.getChatRoomId();
    String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;

    // RabbitMQ 브로커에게 메시지 전달
    template.convertAndSend(destination, payload);
    log.debug("채팅 메시지 브로커 송출 완료 (이벤트 리스너). destination: {}", destination);
  }
}