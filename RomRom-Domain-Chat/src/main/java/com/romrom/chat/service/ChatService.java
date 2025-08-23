package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.MessageType;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ChatService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(UUID me, UUID other) {
    if (me.equals(other)) {
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }
    // 이미 존재하면 아무 작업도 하지 않고 반환

    Optional<ChatRoom> existingRoom = chatRoomRepository.findByMemberAAndMemberB(me, other);
    if (existingRoom.isPresent()) {
      return ChatRoomResponse.builder()
          .roomId(existingRoom.get().getRoomId())
          .build();
    }

    // 없으면 새로 생성
    ChatRoom newRoom = ChatRoom.builder()
        .memberA(me)
        .memberB(other)
        .build();

    chatRoomRepository.save(newRoom);

    return ChatRoomResponse.builder()
        .roomId(newRoom.getRoomId())
        .build();
  }

  // 메시지 저장 (Mongo)
  public ChatMessage saveMessage(UUID roomId, UUID senderId, UUID recipientId,
                                 String content, MessageType type) {
    ChatMessage doc = ChatMessage.builder()
        .roomId(roomId)
        .senderId(senderId)
        .recipientId(recipientId)
        .content(content)
        .type(type)
        .build();
    return chatMessageRepository.save(doc);
  }
}