package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRoomService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatUserStateRepository chatUserStateRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(ChatRoomRequest request) {
    UUID tradeReceiverId = request.getMember().getMemberId();
    UUID tradeSenderId = request.getOpponentMemberId();

    // 자기 자신과의 채팅방 생성 불가
    if (tradeReceiverId.equals(tradeSenderId)) {
      log.error("채팅방 생성 오류 : 본인 회원 ID와 상대방 회원 ID가 같습니다.");
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }

    // 거래 요청 존재 확인
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository
        .findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 거래 요청 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND);
        });

    // 거래 요청이 승인 상태인지 확인
//    if (tradeRequestHistory.getTradeStatus() != TradeStatus.ACCEPTED) {
//      log.error("채팅방 생성 오류 : 거래 요청이 승인 상태가 아닙니다. 현재 상태 = {}", tradeRequestHistory.getTradeStatus().toString());
//      throw new CustomException(ErrorCode.TRADE_REQUEST_NOT_ACCEPTED);
//    }
//    // 상대방 회원 존재 확인
    Member tradeSender = memberRepository.findById(tradeSenderId)
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 상대방 회원 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    // 본인은 거래 요청 받은 사람이어야 함
    if(!tradeRequestHistory.getTakeItem().getMember().getMemberId().equals(tradeReceiverId)) {
      log.error("채팅방 생성 오류 : 거래 요청을 받은 사람만이 채팅방을 생성할 수 있습니다.");
      throw new CustomException(ErrorCode.NOT_TRADE_REQUEST_RECEIVER);
    }

    // 상대방은 거래 요청 보낸 사람이어야 함
    if (!tradeRequestHistory.getGiveItem().getMember().getMemberId().equals(tradeSenderId)) {
      log.error("채팅방 생성 오류 : 상대방 회원이 거래 요청의 당사자가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_TRADE_REQUEST_SENDER);
    }
    // 채팅방 존재 확인 (거래 요청 당 1:1 채팅방)
    ChatRoom chatRoom = chatRoomRepository.findByTradeRequestHistory(tradeRequestHistory)
        .orElseGet(()-> {
          // 없으면 새로 생성
          log.debug("채팅방 생성 : 새로운 1:1 채팅방을 생성합니다.");
          ChatRoom newRoom = ChatRoom.builder()
              .tradeReceiver(request.getMember()) // 본인은 요청을 받은 사람
              .tradeSender(tradeSender)           // 상대방은 요청을 보낸 사람
              .tradeRequestHistory(tradeRequestHistory)
              .build();
          return chatRoomRepository.save(newRoom);
        });

    log.debug("채팅방 멤버 상태 초기화 : 채팅방에 처음 들어온 것으로 간주합니다.");
    ChatUserState tradeReceiverState = ChatUserState.fromRoomIdAndMemberId(chatRoom.getChatRoomId(), tradeReceiverId);
    ChatUserState tradeSenderState = ChatUserState.fromRoomIdAndMemberId(chatRoom.getChatRoomId(), tradeSenderId);
    chatUserStateRepository.save(tradeSenderState);
    chatUserStateRepository.save(tradeReceiverState);

    return ChatRoomResponse.builder()
        .chatRoom(chatRoom)
        .build();
  }

  // 채팅방 목록 조회
  @Transactional(readOnly = true)
  public ChatRoomResponse getRooms(ChatRoomRequest request) {
    Member member = request.getMember();

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    Page<ChatRoom> chatRoomsPage = chatRoomRepository.findByTradeReceiverOrTradeSender(member, member, pageable);
    List<ChatRoom> chatRoomList = chatRoomsPage.getContent();

    if (chatRoomList.isEmpty()) {
      return ChatRoomResponse.builder()
          .chatRooms(chatRoomsPage)
          .unreadCounts(Collections.emptyMap())
          .build();
    }

    // 조회된 채팅방들의 ID 목록 추출
    List<UUID> chatRoomIds = chatRoomList.stream()
        .map(ChatRoom::getChatRoomId) // ChatRoom 엔티티에 getId()가 있다고 가정
        .collect(Collectors.toList());

    Map<UUID, Long> unreadCounts = getUnreadCountsByNPlusOneQuery(member.getMemberId(), chatRoomIds);
    return ChatRoomResponse.builder()
        // member 객체가 영속 상태가 아니더라도 쿼리에 영향 X
        .chatRooms(chatRoomsPage)
        .unreadCounts(unreadCounts)
        .build();
  }


  // 채팅방 삭제
  @Transactional
  public void deleteRoom(ChatRoomRequest request) {
    // TODO : 채팅방 삭제 정책 검토 필요 (양쪽 모두 삭제 시 완전 삭제 vs 한쪽만 삭제 시 상태 변경)
    // 채팅방 존재 및 멤버 확인
    ChatRoom room = validateChatRoomMember(request.getMember().getMemberId(), request.getChatRoomId());
    // 채팅방 삭제
    log.debug("채팅방 삭제 : roomId={}, memberId={}", room.getChatRoomId(), request.getMember().getMemberId());
    chatRoomRepository.delete(room);
    // TODO : 채팅방 메시지 삭제 유무
    log.debug("채팅방 메시지 삭제 : roomId={}", room.getChatRoomId());
    chatMessageRepository.deleteByChatRoomId(room.getChatRoomId());
    log.debug("채팅방 멤버 상태 삭제 : roomId={}", room.getChatRoomId());
    chatUserStateRepository.deleteAllByChatRoomId(room.getChatRoomId());
  }

  // 읽음 커서 갱신
  @Transactional
  public void enterOrLeaveChatRoom(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    UUID chatRoomId = request.getChatRoomId();
    validateChatRoomMember(memberId, chatRoomId);

    ChatUserState chatUserState = chatUserStateRepository.findByChatRoomIdAndMemberId(chatRoomId, memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.NOT_CHATROOM_MEMBER));
    // 입장 시 마지막 읽은 시간 갱신, 퇴장 시 마지막 읽은 시간 유지, lastReadMessageId 갱신
    if(request.isEntered())
      chatUserState.enterChatRoom();
    else
      chatUserState.leaveChatRoom(chatMessageRepository.findTopByChatRoomIdAndSenderIdNotOrderByCreatedDateDesc(chatRoomId, memberId).getChatMessageId());
    // Mongo 는 dirty checking 지원 안함
    chatUserStateRepository.save(chatUserState);
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

  /**
   * 각 채팅방에 대해 count 쿼리를 실행하여 안 읽은 메시지 수를 계산합니다. (N+1 방식)
   * 코드가 직관적이지만, 채팅방 수가 많아지면 성능이 저하될 수 있습니다.
   * @param memberId 현재 사용자 ID
   * @param chatRoomIds 조회할 채팅방 ID 목록
   * @return Map<채팅방ID, 안 읽은 메시지 수>
   */
  private Map<UUID, Long> getUnreadCountsByNPlusOneQuery(UUID memberId, List<UUID> chatRoomIds) {
    // 사용자의 모든 채팅방 상태 정보를 한 번에 조회
    List<ChatUserState> userStates = chatUserStateRepository.findByMemberIdAndChatRoomIdIn(memberId, chatRoomIds);

    // 빠른 조회를 위해 Map<chatRoomId, lastReadAt> 형태로 변환
    Map<UUID, Instant> lastReadAtMap = userStates.stream()
        .collect(Collectors.toMap(ChatUserState::getChatRoomId, ChatUserState::getLastReadAt));

    // 각 채팅방 ID를 순회하며 안 읽은 메시지 수를 계산
    return chatRoomIds.stream()
        .collect(Collectors.toMap(
            roomId -> roomId, // Key
            roomId -> { // Value
              Instant lastReadAt = lastReadAtMap.get(roomId);
              return (lastReadAt == null)
                  ? chatMessageRepository.countByChatRoomId(roomId)
                  : chatMessageRepository.countByChatRoomIdAndCreatedDateAfter(roomId, lastReadAt);
            }
        ));
  }
}