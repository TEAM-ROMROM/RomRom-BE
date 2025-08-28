package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.util.UUID;

import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
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

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final MemberRepository memberRepository;
  private final ChatMessageRepository chatMessageRepository;

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
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.ACCEPTED) {
      log.error("채팅방 생성 오류 : 거래 요청이 승인 상태가 아닙니다. 현재 상태 = {}", tradeRequestHistory.getTradeStatus().toString());
      throw new CustomException(ErrorCode.TRADE_REQUEST_NOT_ACCEPTED);
    }
    // 상대방 회원 존재 확인
    Member tradeSender = memberRepository.findById(tradeSenderId)
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 상대방 회원 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        });

    // 본인은 거래 요청 받은 사람이어야 함
    if(!tradeRequestHistory.getTakeItem().getMember().getMemberId().equals(tradeReceiverId)) {
      log.error("채팅방 생성 오류 : 본인 회원이 거래 요청의 당사자가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_TRADE_REQUEST_MEMBER);
    }

    // 상대방은 거래 요청 보낸 사람이어야 함
    if (!tradeRequestHistory.getGiveItem().getMember().getMemberId().equals(tradeSenderId)) {
      log.error("채팅방 생성 오류 : 상대방 회원이 거래 요청의 당사자가 아닙니다.");
      throw new CustomException(ErrorCode.NOT_TRADE_REQUEST_MEMBER);
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

    return ChatRoomResponse.builder()
        .chatRoom(chatRoom)
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
        .chatRooms(chatRoomRepository.findByTradeReceiverOrTradeSender(me, me, pageable))
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
}