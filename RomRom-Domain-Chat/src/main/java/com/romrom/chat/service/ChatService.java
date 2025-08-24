package com.romrom.chat.service;

import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.dto.ChatRoomRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ChatService {

  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(ChatRoomRequest request) {
    UUID me = request.getMember().getMemberId();
    UUID other = request.getOtherUserId();

    if (me.equals(other)) {
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }

    // 쿼리도 정규화된 순서로 수행 (낮은 UUID → A, 높은 UUID → B)
    Pair normalizedPair = normalizePair(me, other);
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByMemberAAndMemberB(normalizedPair.a, normalizedPair.b);

    // 이미 존재하면 아무 작업도 하지 않고 반환
    if (existingRoom.isPresent()) {
      return ChatRoomResponse.builder()
          .roomId(existingRoom.get().getRoomId())
          .build();
    }

    // 없으면 새로 생성
    ChatRoom newRoom = ChatRoom.builder()
        .memberA(normalizedPair.a())
        .memberB(normalizedPair.b())
        .build();

    chatRoomRepository.save(newRoom);

    return ChatRoomResponse.builder()
        .roomId(newRoom.getRoomId())
        .build();
  }

  // 채팅방 삭제
  public void deleteRoom(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request);

    // 채팅방 삭제
    chatRoomRepository.delete(room);

    // TODO : 채팅방 메시지 삭제 유무
    chatMessageRepository.deleteByRoomId(room.getRoomId());
  }

  // 메시지 저장
  public ChatMessage saveMessage(UUID roomId, ChatMessagePayload payload) {
    ChatMessage doc = ChatMessage.builder()
        .roomId(roomId)
        .senderId(payload.senderId())
        .recipientId(payload.recipientId())
        .content(payload.content())
        .type(payload.type())
        .build();
    return chatMessageRepository.save(doc);
  }

  // 메시지 조회
  public ChatRoomResponse findRecentMessages(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request);

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    return ChatRoomResponse.builder()
        .messages(chatMessageRepository.findByRoomIdOrderByCreatedDateDesc(room.getRoomId(), pageable))
        .roomId(room.getRoomId())
        .build();
  }

  // 채팅방 ID 검증
  @Transactional(readOnly = true)
  public void assertAccessible(UUID roomId) {
    boolean exists = chatRoomRepository.existsByRoomId(roomId);
    if (!exists) throw new CustomException(ErrorCode.CHATROOM_NOT_FOUND);
  }


  // ----------------------------- Private 메서드 ----------------------------- //

  // 채팅방 존재 및 멤버 확인
  private ChatRoom validateChatRoomMember(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID roomId = request.getRoomId();
    ChatRoom room = chatRoomRepository.findByRoomId(roomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    if (!room.getMemberA().equals(memberId) && !room.getMemberB().equals(memberId)) {
      throw new CustomException(ErrorCode.NOT_CHATROOM_MEMBER);
    }
    return room;
  }

  private static Pair normalizePair(UUID x, UUID y) {
    return (x.compareTo(y) <= 0) ? new Pair(x, y) : new Pair(y, x);
  }

  private record Pair(UUID a, UUID b) {}
}