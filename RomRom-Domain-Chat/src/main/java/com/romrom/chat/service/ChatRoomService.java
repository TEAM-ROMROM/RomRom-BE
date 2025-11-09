package com.romrom.chat.service;

import com.google.genai.Chat;
import com.romrom.chat.dto.ChatRoomDetailDto;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
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
  private final MemberLocationRepository memberLocationRepository;

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
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByTradeRequestHistory(tradeRequestHistory);
    if (existingRoom.isPresent()) {
      log.debug("채팅방 조회 : 기존 1:1 채팅방을 반환합니다. ChatRoom ID: {}", existingRoom.get().getChatRoomId());
      // 채팅방이 존재하면, ChatUserState 초기화 없이 바로 반환
      return ChatRoomResponse.builder()
          .chatRoom(existingRoom.get())
          .build();
    }

    // 없으면 새로 생성
    log.debug("채팅방 생성 : 새로운 1:1 채팅방을 생성합니다.");
    ChatRoom newRoom = ChatRoom.builder()
        .tradeReceiver(request.getMember()) // 본인은 요청을 받은 사람
        .tradeSender(tradeSender)           // 상대방은 요청을 보낸 사람
        .tradeRequestHistory(tradeRequestHistory)
        .build();
    ChatRoom chatRoom = chatRoomRepository.save(newRoom);

    log.debug("채팅방 멤버 상태 초기화 : 채팅방에 처음 들어온 것으로 간주합니다.");
    ChatUserState tradeReceiverState = ChatUserState.create(chatRoom.getChatRoomId(), tradeReceiverId);
    ChatUserState tradeSenderState = ChatUserState.create(chatRoom.getChatRoomId(), tradeSenderId);
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
    UUID myMemberId = member.getMemberId();
    log.debug("채팅방 목록 조회 시작. 요청자 ID: {}", myMemberId);

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    // tradeSender, tradeReceiver 모두 페치 조인으로 함께 조회
    Page<ChatRoom> chatRoomsPage = chatRoomRepository.findByTradeReceiverOrTradeSender(member, member, pageable);
    List<ChatRoom> chatRoomList = chatRoomsPage.getContent();
    log.debug("채팅방 목록 조회 완료. 총 {}개 (페이지 {}/{}).", chatRoomList.size(), chatRoomsPage.getNumber(), chatRoomsPage.getTotalPages());

    if (chatRoomList.isEmpty()) {
      return ChatRoomResponse.builder()
          .chatRooms(Page.empty(pageable))
          .build();
    }

    // 조회된 채팅방들의 ID 목록 추출
    List<UUID> chatRoomIds = chatRoomList.stream()
        .map(ChatRoom::getChatRoomId)
        .collect(Collectors.toList());

    // N+1 쿼리로 각 채팅방의 안 읽은 메시지 수 조회
    Map<UUID, Long> unreadCounts = getUnreadCountsByNPlusOneQuery(member.getMemberId(), chatRoomIds);
    log.debug("안 읽은 메시지 수 조회 완료. 총 {}개 방.", unreadCounts.size());

    // 채팅방별 최신 메시지 맵 생성
    List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessageForChatRooms(chatRoomIds);
    Map<UUID, ChatMessage> latestMessageMap = latestMessages.stream()
        .collect(Collectors.toMap(ChatMessage::getChatRoomId, Function.identity(), (first, second) -> first));
    log.debug("가장 최근 메시지 배치 조회 완료. 총 {}개 메시지.", latestMessages.size());

    // 채팅방 대상 멤버 ID 목록 수집 (상대방 회원 ID)
    Set<UUID> targetMemberIds = chatRoomList.stream()
        .map(chatRoomTemp -> chatRoomTemp.getTradeReceiver().getMemberId().equals(myMemberId) ? chatRoomTemp.getTradeSender().getMemberId() : chatRoomTemp.getTradeReceiver().getMemberId())
        .collect(Collectors.toSet());
    log.debug("상대방 회원 ID 목록: {}", targetMemberIds);

    // 위치 정보 일괄 조회 (findByMemberMemberIdIn 사용)
    List<MemberLocation> locations = memberLocationRepository.findByMemberMemberIdIn(targetMemberIds);
    // 빠른 조회를 위해 Map<MemberId, MemberLocation>으로 변환
    Map<UUID, MemberLocation> locationMap = locations.stream()
        .collect(Collectors.toMap(loc -> loc.getMember().getMemberId(), Function.identity(), (first, second) -> first));
    log.debug("상대방 위치 정보 배치 조회 완료. 총 {}개.", locationMap.size());

    // DTO 조립
    List<ChatRoomDetailDto> detailDtoList = chatRoomList.stream().map(chatRoom -> {

      UUID roomId = chatRoom.getChatRoomId();
      ChatMessage lastMsg = latestMessageMap.get(roomId);

      String content;
      LocalDateTime time;
      if (lastMsg == null) {      // 메시지가 없는 경우 (채팅방 생성만 된 상태)
        content = "아직 메시지가 없습니다.";
        time = chatRoom.getCreatedDate();
      }
      else {                      // 메시지가 있는 경우
        content = lastMsg.getContent();
        time = lastMsg.getCreatedDate();
      }

      // 상대방 멤버 찾기 (tradeReceiver 또는 tradeSender)
      Member targetMemberEntity = chatRoom.getTradeReceiver().getMemberId().equals(myMemberId) ? chatRoom.getTradeSender() : chatRoom.getTradeReceiver();

      MemberLocation location = locationMap.get(targetMemberEntity.getMemberId());
      String eupMyeonDong = null;
      if (location != null) {
        eupMyeonDong = location.getEupMyeonDong();
      }
      else {
        log.warn("채팅방 {} 상대방({})의 위치 정보가 DB에 없습니다.", roomId, targetMemberEntity.getMemberId());
      }
      return ChatRoomDetailDto.from(roomId, targetMemberEntity, eupMyeonDong, unreadCounts.getOrDefault(roomId, 0L), content, time);
        }).collect(Collectors.toList());

    // Page<ChatRoom> -> Page<ChatRoomDetailDto>로 변환
    Page<ChatRoomDetailDto> detailPage = new PageImpl<>(detailDtoList, pageable, chatRoomsPage.getTotalElements());

    return ChatRoomResponse.builder()
        .chatRooms(detailPage) // 최종 DTO 페이지 반환
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
    if (request.isEntered())
      chatUserState.enterChatRoom();
    else
      chatUserState.leaveChatRoom();

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
    Map<UUID, ChatUserState> stateMap = userStates.stream()
        .collect(Collectors.toMap(ChatUserState::getChatRoomId, state -> state));

    // 각 채팅방 ID를 순회하며 안 읽은 메시지 수를 계산
    return chatRoomIds.stream()
        .collect(Collectors.toMap(
            roomId -> roomId,
            roomId -> {
              ChatUserState state = stateMap.get(roomId);
              // 만약 "입장 중" (leftAt == null) 이라면, 안 읽은 개수는 0
              log.info("채팅방 ID: {}, leftAt: {}", roomId, state.getLeftAt());
              if (state.getLeftAt() == null) {
                return 0L;
              }

              // 퇴장한 상태라면 퇴장 시 갱신된 leftAt 기준으로 카운트
              LocalDateTime localDateLeftAt = state.getLeftAt();
              return chatMessageRepository.countByChatRoomIdAndCreatedDateAfter(roomId, localDateLeftAt);
            }
        ));
  }
}