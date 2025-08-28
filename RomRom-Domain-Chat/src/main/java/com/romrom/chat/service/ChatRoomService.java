package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.util.Optional;
import java.util.UUID;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
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
public class ChatRoomService {

  private final ItemRepository itemRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(ChatRoomRequest request) {
    // 자기 자신과의 채팅방 생성 불가
    if (request.getMember().getMemberId().equals(request.getOpponentMemberId())) {
      log.error("채팅방 생성 오류 : 본인 회원 ID와 상대방 회원 ID가 같습니다.");
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }

    // 거래 물품 존재 확인
    Item item = itemRepository.findById(request.getItemId())
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 거래 물품 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.ITEM_NOT_FOUND);
        });
    // 상대방 회원 존재 확인
    Member opponentMember = memberRepository.findById(request.getOpponentMemberId())
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 상대방 회원 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    // 채팅방 존재 여부 쿼리도 정규화된 순서로 수행 (낮은 UUID → A, 높은 UUID → B)
    Pair normalizedPair = normalizePair(request.getMember(), opponentMember);
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByMemberAAndMemberBAndItem(
        normalizedPair.getA(), normalizedPair.getB(), item);

    // 이미 존재하면 기존 방 반환
    if (existingRoom.isPresent()) {
      log.debug("이미 만들어진 방이 존재합니다. 해당 방을 반환합니다.");
      return ChatRoomResponse.builder()
          .chatRoom(existingRoom.get())
          .build();
    }

    // 없으면 새로 생성
    log.debug("채팅방 생성 : 새로운 1:1 채팅방을 생성합니다.");
    ChatRoom newRoom = ChatRoom.builder()
        .memberA(normalizedPair.getA())
        .memberB(normalizedPair.getB())
        .item(item)
        .build();

    chatRoomRepository.save(newRoom);

    return ChatRoomResponse.builder()
        .chatRoom(newRoom)
        .build();
  }

  // 채팅방 목록 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse getRooms(ChatRoomRequest request) {
    Member me = request.getMember();

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    return ChatRoomResponse.builder()
        // me 객체가 영속 상태가 아니더라도 쿼리에 영향 X
        .chatRooms(chatRoomRepository.findByMemberAOrMemberB(me, me, pageable))
        .build();
  }


  // 채팅방 삭제
  @Transactional
  public void deleteRoom(ChatRoomRequest request) {
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    // 채팅방 삭제
    log.debug("채팅방 삭제 : roomId={}, memberId={}", room.getChatRoomId(), request.getMember().getMemberId());
    chatRoomRepository.delete(room);
    // TODO : 채팅방 메시지 삭제 유무
    log.debug("채팅방 메시지 삭제 : roomId={}", room.getChatRoomId());
    chatMessageRepository.deleteByChatRoomId(room.getChatRoomId());
  }


  // 채팅방 존재 및 멤버 확인
  public ChatRoom validateChatRoomMember(UUID memberId, UUID chatRoomId) {
    ChatRoom chatRoom = chatRoomRepository.findByChatRoomId(chatRoomId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND));

    if (!chatRoom.isMember(memberId)) {
      log.error("채팅방 회원 검증 오류 : 요청자는 채팅방 멤버가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_CHATROOM_MEMBER);
    }
    return chatRoom;
  }

  // ----------------------------- Private 메서드 ----------------------------- //


  private static Pair normalizePair(Member x, Member y) {
    return (x.getMemberId().compareTo(y.getMemberId()) <= 0) ? new Pair(x, y) : new Pair(y, x);
  }

  @Getter
  private static class Pair {
    private final Member a;
    private final Member b;
    public Pair(Member a, Member b) {
      this.a = a;
      this.b = b;
    }
  }
}