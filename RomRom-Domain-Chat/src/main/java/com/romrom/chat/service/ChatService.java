package com.romrom.chat.service;

import com.romrom.chat.dto.ChatMessagePayload;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.util.Optional;
import java.util.UUID;

import com.romrom.member.repository.MemberRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

  private final ChatRoomRepository chatRoomRepository;
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(ChatRoomRequest request) {
    UUID requesterId = request.getMember().getMemberId();
    UUID opponentMemberId = request.getOpponentMemberId();

    // 회원 존재 확인
    if (memberRepository.existsById(opponentMemberId)) {
      log.error("채팅방 생성 오류 : 상대방 회원 ID가 존재하지 않습니다.");
      throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
    }
    // 자기 자신과의 채팅방 생성 불가
    if (requesterId.equals(opponentMemberId)) {
      log.error("채팅방 생성 오류 : 본인 회원 ID와 상대방 회원 ID가 같습니다.");
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }

    // 쿼리도 정규화된 순서로 수행 (낮은 UUID → A, 높은 UUID → B)
    Pair normalizedPair = normalizePair(requesterId, opponentMemberId);
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByMemberAAndMemberB(normalizedPair.getA(), normalizedPair.getB());

    // 이미 존재하면 기존 방 반환
    if (existingRoom.isPresent()) {
      log.debug("이미 만들어진 방이 존재합니다. 해당 방을 반환합니다.");
      return ChatRoomResponse.builder()
          .chatRoomId(existingRoom.get().getChatRoomId())
          .build();
    }

    // 없으면 새로 생성
    log.debug("채팅방 생성 : 새로운 1:1 채팅방을 생성합니다.");
    ChatRoom newRoom = ChatRoom.builder()
        .memberA(normalizedPair.getA())
        .memberB(normalizedPair.getB())
        .build();
    chatRoomRepository.save(newRoom);

    return ChatRoomResponse.builder()
        .chatRoomId(newRoom.getChatRoomId())
        .build();
  }

  @Transactional(readOnly = true)
  public ChatRoomResponse getRooms(ChatRoomRequest request) {
    UUID me = request.getMember().getMemberId();

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    return ChatRoomResponse.builder()
        .chatRooms(chatRoomRepository.findByMemberAOrMemberB(me, me, pageable))
        .build();
  }


  // 채팅방 삭제
  public void deleteRoom(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request);

    log.debug("채팅방 삭제 : roomId={}, memberId={}", room.getChatRoomId(), request.getMember().getMemberId());
    // 채팅방 삭제
    chatRoomRepository.delete(room);

    log.debug("채팅방 메시지 삭제 : roomId={}", room.getChatRoomId());
    // TODO : 채팅방 메시지 삭제 유무
    chatMessageRepository.deleteByChatRoomId(room.getChatRoomId());
  }

  // 메시지 저장
  public ChatMessage saveMessage(UUID chatRoomId, ChatMessagePayload payload) {
    ChatMessage chatMessage = ChatMessage.builder()
        .chatRoomId(chatRoomId)
        .senderId(payload.getSenderId())
        .recipientId(payload.getRecipientId())
        .content(payload.getContent())
        .type(payload.getType())
        .build();
    return chatMessageRepository.save(chatMessage);
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
        .messages(chatMessageRepository.findByChatRoomIdOrderByCreatedDateDesc(room.getChatRoomId(), pageable))
        .chatRoomId(room.getChatRoomId())
        .build();
  }

  // 채팅방 ID 검증
  @Transactional(readOnly = true)
  public void assertAccessible(UUID roomId) {
    boolean exists = chatRoomRepository.existsByChatRoomId(roomId);
    if (!exists) throw new CustomException(ErrorCode.CHATROOM_NOT_FOUND);
  }


  // ----------------------------- Private 메서드 ----------------------------- //

  // 채팅방 존재 및 멤버 확인
  private ChatRoom validateChatRoomMember(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID roomId = request.getChatRoomId();
    ChatRoom room = chatRoomRepository.findByChatRoomId(roomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    if (!room.getMemberA().equals(memberId) && !room.getMemberB().equals(memberId)) {
      log.error("채팅방 회원 검증 오류 : 요청자는 채팅방 멤버가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_CHATROOM_MEMBER);
    }
    return room;
  }

  private static Pair normalizePair(UUID x, UUID y) {
    return (x.compareTo(y) <= 0) ? new Pair(x, y) : new Pair(y, x);
  }

  @Getter
  private static class Pair {
    private final UUID a;
    private final UUID b;
    public Pair(UUID a, UUID b) {
      this.a = a;
      this.b = b;
    }
  }
}