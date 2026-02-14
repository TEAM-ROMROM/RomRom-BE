package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomDetailDto;
import com.romrom.chat.dto.ChatRoomRequest;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.dto.ChatRoomType;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.chat.repository.postgres.ChatRoomRepository;
import com.romrom.common.constant.TradeStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.service.MemberBlockService;
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
  private final ChatMessageRepository chatMessageRepository;
  private final ChatUserStateRepository chatUserStateRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberBlockService memberBlockService;
  private final ItemImageRepository itemImageRepository;

  @Transactional
  public ChatRoomResponse createOneToOneRoom(ChatRoomRequest request) {
    UUID tradeReceiverId = request.getMember().getMemberId();
    UUID tradeSenderId = request.getOpponentMemberId();

    // 자기 자신과의 채팅방 생성 불가
    if (tradeReceiverId.equals(tradeSenderId)) {
      log.error("채팅방 생성 오류 : 본인 회원 ID와 상대방 회원 ID가 같습니다.");
      throw new CustomException(ErrorCode.CANNOT_CREATE_SELF_CHATROOM);
    }

    // 차단된 상대방인지 확인
    memberBlockService.verifyNotBlocked(tradeReceiverId, tradeSenderId);

    // 채팅방 존재 확인 (거래 요청 당 1:1 채팅방)
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByTradeRequestHistoryId(request.getTradeRequestHistoryId());
    if (existingRoom.isPresent()) {
      log.debug("채팅방 조회 : 기존 1:1 채팅방을 반환합니다. ChatRoom ID: {}", existingRoom.get().getChatRoomId());
      // 채팅방이 존재하면, ChatUserState 초기화 없이 바로 반환
      return ChatRoomResponse.builder()
          .chatRoom(existingRoom.get())
          .build();
    }

    // 거래 요청 존재 확인
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository
        .findByTradeRequestHistoryIdWithItems(request.getTradeRequestHistoryId())
        .orElseThrow(() -> {
          log.error("채팅방 생성 오류 : 거래 요청 ID가 존재하지 않습니다.");
          return new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND);
        });

    // 거래 요청이 대기 상태인지 확인
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("채팅방 생성 오류 : 거래 요청이 대기중 상태가 아닙니다. 현재 상태 = {}", tradeRequestHistory.getTradeStatus().toString());
      throw new CustomException(ErrorCode.TRADE_REQUEST_NOT_PENDING);
    }

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

    // 검증 로직 이후 생성
    log.debug("채팅방 생성 : 새로운 1:1 채팅방을 생성합니다.");
    tradeRequestHistory.startChatting();    // 거래요청을 채팅중 상태로 변경
    ChatRoom newRoom = ChatRoom.builder()
        .tradeReceiver(request.getMember())                             // 본인은 요청을 받은 사람
        .tradeSender(tradeRequestHistory.getGiveItem().getMember())     // 상대방은 요청을 보낸 사람
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
    // chatroomdetails DTO 에 보낸요청, 받은 요청 필드 추가
    Slice<ChatRoom> chatRoomsSlice = chatRoomRepository.findByTradeReceiverOrTradeSender(member, member, pageable);
    List<ChatRoom> chatRoomList = chatRoomsSlice.getContent();
    log.debug("채팅방 목록 조회 완료. 현재 페이지 데이터: {}개.", chatRoomList.size());

    if (chatRoomList.isEmpty()) {
      return ChatRoomResponse.builder()
          .chatRoomDetailDtoPage(Page.empty(pageable))
          .build();
    }
    // 조립을 위한 벌크 데이터 준비
    List<UUID> chatRoomIds = chatRoomList.stream().map(ChatRoom::getChatRoomId).collect(Collectors.toList());

    Map<UUID, Long> unreadCounts = getUnreadCountsByNPlusOneQuery(myMemberId, chatRoomIds);
    log.debug("안 읽은 메시지 수 조회 완료. 총 {}개 방.", unreadCounts.size());

    Map<UUID, ChatMessage> latestMessageMap = fetchLatestMessageMap(chatRoomIds);
    Set<UUID> targetMemberIds = fetchTargetMemberIds(chatRoomList, myMemberId);
    Map<UUID, MemberLocation> locationMap = fetchLocationMap(targetMemberIds);
    Set<UUID> blockedMemberIds = fetchBlockedMemberIds(myMemberId, targetMemberIds);
    Map<UUID, String> itemImageMap = fetchItemImageMap(chatRoomList);

    // 필터링 및 DTO 조립
    List<ChatRoomDetailDto> detailDtoList = chatRoomList.stream()
        .filter(chatRoom -> {
          Long count = unreadCounts.get(chatRoom.getChatRoomId());
          return count != null && count != -1L; // 삭제된 방(-1L) 필터링
        })
        .map(chatRoom -> convertToDetailDto(chatRoom, myMemberId, unreadCounts, latestMessageMap, locationMap, blockedMemberIds, itemImageMap))
        .collect(Collectors.toList());

    Slice<ChatRoomDetailDto> detailSlice = new SliceImpl<>(detailDtoList, pageable, chatRoomsSlice.hasNext());

    return ChatRoomResponse.builder()
        .chatRoomDetailDtoPage(detailSlice)
        .build();
  }


  // 채팅방 삭제
  @Transactional(transactionManager = "chainedTransactionManager")
  public void deleteRoom(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    ChatRoom room = validateChatRoomMember(memberId, request.getChatRoomId());
    handleRoomExit(room, memberId);
  }

  /**
   * 회원 삭제 시 관련된 모든 ChatRoom 삭제
   * 회원이 tradeReceiver 또는 tradeSender로 참여한 모든 ChatRoom을 삭제합니다.
   * ChatMessage, ChatUserState도 함께 삭제됩니다.
   *
   * @param memberId 삭제할 회원 ID
   */
  @Transactional(transactionManager = "chainedTransactionManager")  // PostgreSQL + MongoDB 트랜잭션 관리
  public void deleteAllChatRoomsByMemberId(UUID memberId) {
    List<ChatRoom> myRooms = chatRoomRepository.findAllByTradeSender_MemberIdAndTradeReceiver_MemberId(memberId, memberId);
    if (myRooms.isEmpty()) return;
    // 각 방에 대해 나가기 로직 수행
    for (ChatRoom room : myRooms) {
      handleRoomExit(room, memberId);
    }
    log.debug("회원({})의 모든 채팅방 처리가 완료되었습니다. (Soft/Hard Delete 혼합)", memberId);
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


  // --- Private Helper Methods ---

  /**
   * [공통 로직] 채팅방 나가기 프로세스
   * 1. 거래 상태를 CANCELLED로 변경 (상대방 전송 차단)
   * 2. 상대방도 이미 나갔다면? Hard Delete (완전 삭제)
   * 3. 상대방이 아직 남아있다면? Soft Delete (내 상태만 삭제 표시)
   */
  private void handleRoomExit(ChatRoom room, UUID myId) {
    UUID roomId = room.getChatRoomId();

    // CHATTING 상태면 거래취소로 변경 (상대방이 더 이상 메시지를 못 보내게 함)
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(room.getTradeRequestHistory().getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
    tradeRequestHistory.changeToCancelIfChatting();

    // 상대방 상태 확인
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(roomId, myId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (opponentState.isDeleted()) {      // 시나리오: 상대방도 이미 나간 경우 -> 진짜 다 지움 (Hard Delete)
      executeHardDelete(roomId);
    } else {                              // 시나리오: 상대방은 아직 남아있는 경우 -> 내 상태만 비표시 (Soft Delete)
      log.debug("상대방이 남아있어 내 상태만 삭제 표시합니다. roomId={}, myId={}", roomId, myId);
      ChatUserState myState = chatUserStateRepository.findByChatRoomIdAndMemberId(roomId, myId)
          .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));
      myState.removeRoom(); // removedAt 설정
      chatUserStateRepository.save(myState);
    }
  }

  /**
   * DB에서 모든 흔적을 지우는 편의 메서드
   */
  private void executeHardDelete(UUID roomId) {
    log.debug("채팅방에 다른 멤버가 나간 상태이므로, 채팅방을 완전 삭제합니다. roomId={}", roomId);
    chatRoomRepository.deleteById(roomId);
    log.debug("채팅방 메시지 삭제 : roomId={}", roomId);
    chatMessageRepository.deleteByChatRoomId(roomId);
    log.debug("채팅방 멤버 상태 삭제 : roomId={}", roomId);
    chatUserStateRepository.deleteAllByChatRoomId(roomId);
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

              if (state == null) {
                log.warn("CharUserState가 존재하지 않는 ChatRoom 발견. CharUserState를 새로 생성합니다. : chatRoomId: {}, memberId: {}", roomId, memberId);
                chatUserStateRepository.save(ChatUserState.create(roomId, memberId));
                return 0L; // 안 읽은 메시지 0개로 처리
              }

              // 만약 삭제된 방이라면, 필터링용으로 -1 반환
              if (state.isDeleted()) return -1L;

              // 만약 "입장 중" (leftAt == null) 이라면, 안 읽은 개수는 0
              log.debug("채팅방 ID: {}, leftAt: {}", roomId, state.getLeftAt());
              if (state.getLeftAt() == null) {
                return 0L;
              }

              // 퇴장한 상태라면 퇴장 시 갱신된 leftAt 기준으로 카운트
              LocalDateTime localDateLeftAt = state.getLeftAt();
              // 본인이 보낸 메시지는 제외
              return chatMessageRepository.countByChatRoomIdAndCreatedDateAfterAndSenderIdNot(roomId, localDateLeftAt, memberId);
            }
        ));
  }

  private Map<UUID, ChatMessage> fetchLatestMessageMap(List<UUID> chatRoomIds) {
    List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessageForChatRooms(chatRoomIds);
    Map<UUID, ChatMessage> latestMessageMap = latestMessages.stream()
        .collect(Collectors.toMap(ChatMessage::getChatRoomId, Function.identity(), (first, second) -> first));
    log.debug("가장 최근 메시지 배치 조회 완료. 총 {}개 메시지.", latestMessages.size());
    return latestMessageMap;
  }

  private Set<UUID> fetchTargetMemberIds(List<ChatRoom> chatRoomList, UUID myMemberId) {
    Set<UUID> targetMemberIds = chatRoomList.stream()
        .map(chatRoomTemp -> chatRoomTemp.getTradeReceiver().getMemberId().equals(myMemberId) ? chatRoomTemp.getTradeSender().getMemberId() : chatRoomTemp.getTradeReceiver().getMemberId())
        .collect(Collectors.toSet());
    log.debug("상대방 회원 ID 목록: {}", targetMemberIds);
    return targetMemberIds;
  }

  private Map<UUID, MemberLocation> fetchLocationMap(Set<UUID> targetMemberIds) {
    List<MemberLocation> locations = memberLocationRepository.findByMemberMemberIdIn(targetMemberIds);
    Map<UUID, MemberLocation> locationMap = locations.stream()
        .collect(Collectors.toMap(loc -> loc.getMember().getMemberId(), Function.identity(), (first, second) -> first));
    log.debug("상대방 위치 정보 배치 조회 완료. 총 {}개.", locationMap.size());
    return locationMap;
  }

  private Set<UUID> fetchBlockedMemberIds(UUID myMemberId, Set<UUID> targetMemberIds) {
    List<MemberBlock> blockRelations = memberBlockService.getMemberBlockList(myMemberId, targetMemberIds);
    Set<UUID> blockedMemberIds = blockRelations.stream()
        .map(mb -> mb.getBlockerMember().getMemberId().equals(myMemberId) ? mb.getBlockedMember().getMemberId() : mb.getBlockerMember().getMemberId())
        .collect(Collectors.toSet());
    log.debug("차단 관계 확인 완료. 차단된 상대방 수: {}", blockedMemberIds.size());
    return blockedMemberIds;
  }

  private Map<UUID, String> fetchItemImageMap(List<ChatRoom> chatRoomList) {
    // 모든 채팅방의 takeItem, giveItem ID 수집
    List<UUID> itemIds = chatRoomList.stream()
        .flatMap(chatRoom -> {
          TradeRequestHistory trh = chatRoom.getTradeRequestHistory();
          return java.util.stream.Stream.of(
              trh.getTakeItem().getItemId(),
              trh.getGiveItem().getItemId()
          );
        })
        .distinct()
        .collect(Collectors.toList());

    if (itemIds.isEmpty()) return Collections.emptyMap();

    // 벌크 조회 (createdDate ASC 정렬 + Item fetch join) 후 itemId → 첫 번째 imageUrl 매핑
    List<ItemImage> itemImages = itemImageRepository.findAllByItemIdsWithItemOrderByCreatedDate(itemIds);
    Map<UUID, String> imageMap = new HashMap<>();
    for (ItemImage img : itemImages) {
      imageMap.putIfAbsent(img.getItem().getItemId(), img.getImageUrl());
    }
    log.debug("물품 이미지 벌크 조회 완료. 총 {}개 물품의 대표 이미지.", imageMap.size());
    return imageMap;
  }

  private ChatRoomDetailDto convertToDetailDto(ChatRoom chatRoom, UUID myMemberId, Map<UUID, Long> unreadCounts,
                                               Map<UUID, ChatMessage> latestMessageMap, Map<UUID, MemberLocation> locationMap,
                                               Set<UUID> blockedMemberIds, Map<UUID, String> itemImageMap) {
    UUID roomId = chatRoom.getChatRoomId();
    ChatMessage lastMsg = latestMessageMap.get(roomId);

    String content;
    LocalDateTime time;
    if (lastMsg == null) {
      content = "아직 메시지가 없습니다.";
      time = chatRoom.getCreatedDate();
    } else {
      content = lastMsg.getContent();
      time = lastMsg.getCreatedDate();
    }

    Member targetMemberEntity;
    ChatRoomType chatRoomType;
    UUID targetItemId;
    if (chatRoom.getTradeReceiver().getMemberId().equals(myMemberId)) {
      targetMemberEntity = chatRoom.getTradeSender();
      chatRoomType = ChatRoomType.RECEIVED;
      // 내가 tradeReceiver → 상대방 물품 = giveItem (tradeSender가 보낸 물품)
      targetItemId = chatRoom.getTradeRequestHistory().getGiveItem().getItemId();
    } else {
      targetMemberEntity = chatRoom.getTradeReceiver();
      chatRoomType = ChatRoomType.REQUESTED;
      // 내가 tradeSender → 상대방 물품 = takeItem (tradeReceiver의 물품)
      targetItemId = chatRoom.getTradeRequestHistory().getTakeItem().getItemId();
    }
    targetMemberEntity.setOnlineIfActiveWithin90Seconds();

    MemberLocation location = locationMap.get(targetMemberEntity.getMemberId());
    String eupMyeonDong = null;
    if (location != null) {
      eupMyeonDong = location.getEupMyoenDong();
    } else {
      log.warn("채팅방 {} 상대방({})의 위치 정보가 DB에 없습니다.", roomId, targetMemberEntity.getMemberId());
    }

    String targetItemImageUrl = itemImageMap.get(targetItemId);

    boolean isBlocked = blockedMemberIds.contains(targetMemberEntity.getMemberId());
    return ChatRoomDetailDto.from(roomId, isBlocked, targetMemberEntity, eupMyeonDong, unreadCounts.getOrDefault(roomId, 0L), content, time, chatRoomType, targetItemImageUrl);
  }
}