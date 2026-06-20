package com.romrom.chat.service;

import com.romrom.chat.dto.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRoomService {

  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatWebSocketService chatWebSocketService;
  private final ChatUserStateRepository chatUserStateRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberBlockService memberBlockService;
  private final ItemImageRepository itemImageRepository;
  private final ChatMessageService chatMessageService;
  private final ChatUserStateEnsureService chatUserStateEnsureService;

  // 같은 클래스의 트랜잭션 메서드를 프록시 경유로 호출하기 위한 self 참조
  // (this 직접 호출은 @Transactional 경계가 적용되지 않음)
  @Lazy
  @Autowired
  private ChatRoomService self;

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

    // 차단된 상대방인지 확인
    memberBlockService.verifyNotBlocked(tradeReceiverId, tradeSenderId);

    // 채팅방 존재 확인 (거래 요청 당 1:1 채팅방)
    Optional<ChatRoom> existingRoom = chatRoomRepository.findByTradeRequestHistoryId(request.getTradeRequestHistoryId());
    if (existingRoom.isPresent()) {
      log.debug("채팅방 조회 : 기존 1:1 채팅방을 반환합니다. ChatRoom ID: {}", existingRoom.get().getChatRoomId());
      return ChatRoomResponse.builder()
          .chatRoom(existingRoom.get())
          .build();
    }

    // 거래 요청이 대기 상태인지 확인
    if (tradeRequestHistory.getTradeStatus() != TradeStatus.PENDING) {
      log.error("채팅방 생성 오류 : 거래 요청이 대기중 상태가 아닙니다. 현재 상태 = {}", tradeRequestHistory.getTradeStatus().toString());
      throw new CustomException(ErrorCode.TRADE_REQUEST_NOT_PENDING);
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

  /**
   * 1:1 채팅방 생성 진입점.
   *
   * 채팅방 생성은 "존재 확인 후 save" 구조라, 동일 거래 요청에 대해 두 요청이 거의 동시에
   * 존재 확인을 통과하면 두 번째 save가 unique 제약을 위반해 500이 발생한다.
   * 제약 위반은 곧 다른 요청이 먼저 방을 만들었다는 뜻이므로, 예외를 흡수하고 기존 방을 반환한다.
   * 제약 위반으로 오염된 트랜잭션에서는 추가 조회가 불가능하므로, 생성과 재조회 트랜잭션을 분리한다.
   */
  public ChatRoomResponse createOneToOneRoomSafely(ChatRoomRequest request) {
    try {
      return self.createOneToOneRoom(request);
    } catch (DataIntegrityViolationException e) {
      UUID tradeRequestHistoryId = request.getTradeRequestHistoryId();
      log.debug("채팅방 동시 생성 충돌 : 먼저 생성된 기존 방을 반환합니다. tradeRequestHistoryId={}",
          tradeRequestHistoryId);
      ChatRoom alreadyCreatedRoom = self.findExistingRoom(tradeRequestHistoryId)
          .orElseThrow(() -> {
            // unique 위반인데 재조회도 실패하면 다른 무결성 오류이므로 그대로 알린다.
            log.error("채팅방 동시 생성 충돌 처리 실패 : 제약 위반 후 기존 방 재조회 불가. tradeRequestHistoryId={}",
                tradeRequestHistoryId, e);
            return new CustomException(ErrorCode.CHATROOM_NOT_FOUND);
          });
      return ChatRoomResponse.builder()
          .chatRoom(alreadyCreatedRoom)
          .build();
    }
  }

  // 생성 트랜잭션과 분리된 새 트랜잭션에서 거래 요청에 해당하는 채팅방을 조회한다.
  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public Optional<ChatRoom> findExistingRoom(UUID tradeRequestHistoryId) {
    return chatRoomRepository.findByTradeRequestHistoryId(tradeRequestHistoryId);
  }

  // 채팅방 목록 조회
  @Transactional
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
    Slice<ChatRoom> chatRoomsSlice = chatRoomRepository.findByTradeReceiverOrTradeSender(member, member, withOneMoreRow(pageable));
    log.debug("채팅방 목록 조회 완료. 현재 페이지 데이터: {}개.", chatRoomsSlice.getContent().size());

    return buildChatRoomDetailResponse(chatRoomsSlice, myMemberId, pageable);
  }

  // 물품 ID 기반 채팅방 목록 조회
  @Transactional
  public ChatRoomResponse getRoomsByItemId(ChatRoomRequest request) {
    Member member = request.getMember();
    UUID myMemberId = member.getMemberId();
    UUID itemId = request.getItemId();
    log.debug("물품별 채팅방 목록 조회 시작. 요청자 ID: {}, 물품 ID: {}", myMemberId, itemId);

    Pageable pageable = PageRequest.of(
        request.getPageNumber(),
        request.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdDate")
    );

    Slice<ChatRoom> chatRoomsSlice = chatRoomRepository.findByMemberAndItemId(member, itemId, withOneMoreRow(pageable));
    log.debug("물품별 채팅방 목록 조회 완료. 현재 페이지 데이터: {}개.", chatRoomsSlice.getContent().size());

    return buildChatRoomDetailResponse(chatRoomsSlice, myMemberId, pageable);
  }

  // 나간 방 필터링으로 페이지가 줄어드는 것을 보정하기 위해, 같은 offset에서 한 건 더 조회한다.
  private Pageable withOneMoreRow(Pageable pageable) {
    return OffsetLimitPageable.of(pageable.getOffset(), pageable.getPageSize() + 1, pageable.getSort());
  }

  // 채팅방 삭제
  @Transactional
  public void deleteRoom(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    ChatRoom room = validateChatRoomMember(memberId, request.getChatRoomId());
    handleRoomExit(room, memberId);
  }

  /**
   * 회원 삭제 시 관련된 모든 ChatRoom 삭제
   * 회원이 tradeReceiver 또는 tradeSender로 참여한 모든 ChatRoom에 나가기 처리합니다.
   *
   * @param memberId 삭제할 회원 ID
   */
  @Transactional
  public void deleteAllChatRoomsByMemberId(UUID memberId) {
    List<ChatRoom> myRooms = chatRoomRepository.findAllByTradeSender_MemberIdOrTradeReceiver_MemberId(memberId, memberId);
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
    ChatRoom room = validateChatRoomMember(memberId, chatRoomId);
    ChatUserState myState = chatUserStateRepository.findByChatRoomIdAndMemberId(room.getChatRoomId(), memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (Boolean.TRUE.equals(request.getIsEntered())) {
      leaveOtherActiveChatRooms(memberId, chatRoomId);
      myState.enterChatRoom();
      chatUserStateRepository.save(myState);
      // 상태 저장 커밋 완료 후 읽음 이벤트 발송 (ChatMessageService.registerMessageDispatch 와 동일한 패턴)
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          sendReadEventIfOpponentPresent(myState);
        }
      });
    } else {
      leaveChatRoomPresence(myState);
    }
  }

  // 웹소켓 연결이 비정상 종료된 경우에도 현재 열린 채팅방만 퇴장 처리한다.
  @Transactional
  public void leaveActiveChatRooms(UUID memberId) {
    List<ChatUserState> activeStates = chatUserStateRepository.findByMemberIdAndLeftAtIsNull(memberId);
    if (activeStates.isEmpty()) {
      return;
    }

    log.debug("웹소켓 연결 종료로 활성 채팅방 퇴장 처리를 시작합니다. memberId={}, activeRoomCount={}",
        memberId, activeStates.size());
    activeStates.forEach(this::leaveChatRoomPresence);
  }

  @Transactional
  public ChatRoomResponse getOpponentState(ChatRoomRequest request) {
    UUID memberId = request.getMember().getMemberId();
    ChatRoom room = validateChatRoomMember(memberId, request.getChatRoomId());
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(room.getChatRoomId(), memberId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    return ChatRoomResponse.builder()
        .opponentState(opponentState)
        .build();
  }

  // --- Private Helper Methods ---

  // 한 계정이 동시에 여러 방을 보고 있는 상태가 남지 않도록 입장 전에 기존 active 방을 정리한다.
  private void leaveOtherActiveChatRooms(UUID memberId, UUID currentChatRoomId) {
    List<ChatUserState> activeStates = chatUserStateRepository.findByMemberIdAndLeftAtIsNull(memberId);
    activeStates.stream()
        .filter(state -> !currentChatRoomId.equals(state.getChatRoomId()))
        .forEach(this::leaveChatRoomPresence);
  }

  // 퇴장/연결 종료는 읽음 이벤트를 보내지 않고 DB 커서만 닫는다.
  private void leaveChatRoomPresence(ChatUserState myState) {
    myState.leaveChatRoom();
    chatUserStateRepository.save(myState);
  }

  private void sendReadEventIfOpponentPresent(ChatUserState myState) {
    ChatUserState opponentState = chatUserStateRepository
        .findByChatRoomIdAndMemberIdNot(myState.getChatRoomId(), myState.getMemberId())
        .orElse(null);
    if (opponentState == null) {
      log.warn("읽음 이벤트 전송 대상 상대방 상태를 찾을 수 없습니다. roomId={}, memberId={}",
          myState.getChatRoomId(), myState.getMemberId());
      return;
    }

    if (!opponentState.isDeleted() && opponentState.isPresent()) {
      log.debug("상대방이 현재 화면에 있으므로, 읽음 이벤트를 브로커로 송출합니다. roomId={}, memberId={}",
          myState.getChatRoomId(), myState.getMemberId());
      chatWebSocketService.sendReadEvent(myState);
    }
  }

  /**
   * ChatRoom 슬라이스로부터 벌크 데이터를 조회하고, 삭제/나간 채팅방을 필터링한 뒤
   * ChatRoomDetailDto 목록을 조립하여 ChatRoomResponse를 반환하는 공통 로직
   */
  private ChatRoomResponse buildChatRoomDetailResponse(Slice<ChatRoom> chatRoomsSlice, UUID myMemberId, Pageable pageable) {
    List<ChatRoom> chatRoomList = chatRoomsSlice.getContent();

    if (chatRoomList.isEmpty()) {
      return ChatRoomResponse.builder()
          .chatRoomDetailDtoPage(Page.empty(pageable))
          .build();
    }

    // 조립을 위한 벌크 데이터 준비
    List<UUID> chatRoomIds = chatRoomList.stream().map(ChatRoom::getChatRoomId).collect(Collectors.toList());
    long expectedStateCount = (long) chatRoomIds.size() * 2;
    long actualStateCount = chatUserStateRepository.countByChatRoomIdIn(chatRoomIds);
    if (actualStateCount != expectedStateCount) {
      log.warn("ChatUserState 개수가 예상과 달라 자동 복구를 시도합니다. expected={}, actual={}, roomCount={}",
          expectedStateCount, actualStateCount, chatRoomIds.size());
      chatUserStateEnsureService.ensureStates(chatRoomList);
    }

    Map<UUID, Long> unreadCounts = getUnreadCounts(myMemberId, chatRoomIds);
    log.debug("안 읽은 메시지 수 조회 완료. 총 {}개 방.", unreadCounts.size());

    Map<UUID, ChatMessage> latestMessageMap = fetchLatestMessageMap(chatRoomIds);
    Set<UUID> targetMemberIds = fetchTargetMemberIds(chatRoomList, myMemberId);
    Map<UUID, MemberLocation> locationMap = fetchLocationMap(targetMemberIds);
    Set<UUID> blockedMemberIds = fetchBlockedMemberIds(myMemberId, targetMemberIds);
    Map<UUID, String> itemImageMap = fetchItemImageMap(chatRoomList);

    // 삭제/나간 채팅방 필터링 및 DTO 조립
    List<ChatRoomDetailDto> detailDtoList = chatRoomList.stream()
        .filter(chatRoom -> {
          Long count = unreadCounts.get(chatRoom.getChatRoomId());
          // 한쪽만 나간 방(ChatUserState 기준 -1L) 필터링. 양쪽 나간 deletedAt 방은 쿼리에서 이미 제외됨
          return count != null && count != -1L;
        })
        .map(chatRoom -> convertToDetailDto(chatRoom, myMemberId, unreadCounts, latestMessageMap, locationMap, blockedMemberIds, itemImageMap))
        .collect(Collectors.toList());

    // 한 페이지를 채우기 위해 요청 크기보다 한 건 더 조회했다(withOneMoreRow).
    // 필터링 후에도 요청 크기를 초과해 남으면 다음 페이지가 확실히 존재하므로 잘라서 반환한다.
    // 그렇지 않으면, 필터링 전 원본이 추가 조회분까지 꽉 찼는지로 다음 페이지 존재 여부를 판단한다.
    // (Page.hasNext는 offset 기반 커스텀 Pageable에서 부정확하므로 사용하지 않는다.)
    int pageSize = pageable.getPageSize();
    boolean hasNext;
    if (detailDtoList.size() > pageSize) {
      detailDtoList = detailDtoList.subList(0, pageSize);
      hasNext = true;
    } else {
      hasNext = chatRoomList.size() > pageSize;
    }

    Slice<ChatRoomDetailDto> detailSlice = new SliceImpl<>(detailDtoList, pageable, hasNext);

    return ChatRoomResponse.builder()
        .chatRoomDetailDtoPage(detailSlice)
        .build();
  }

  /**
   * [공통 로직] 채팅방 나가기 프로세스
   * 1. 거래 상태를 CANCELLED로 변경 (상대방 전송 차단)
   * 2. 상대방도 이미 나갔다면? ChatRoom.deletedAt 표시 (물리삭제는 배치가 수행 #750)
   * 3. 상대방이 아직 남아있다면? 내 상태만 삭제 표시 + 시스템 메시지 전송
   */
  private void handleRoomExit(ChatRoom room, UUID myId) {
    UUID roomId = room.getChatRoomId();

    // CHATTING 상태면 거래취소로 변경 (상대방이 더 이상 메시지를 못 보내게 함)
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(room.getTradeRequestHistory().getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
    tradeRequestHistory.changeToCancelIfChatting();

    // 내 상태를 원자적으로 removed 표시 (removedAt == null인 경우에만 갱신).
    // 동시 퇴장 race condition 방지: read-modify-write를 단일 findAndModify로 대체한다.
    ChatUserState myRemovedState = chatUserStateRepository.markRemovedIfNotRemoved(roomId, myId);
    if (myRemovedState == null) {
      // 이미 내가 나간 방을 다시 나가려는 중복 요청 — 추가 처리 불필요
      log.debug("이미 삭제 표시된 채팅방에 대한 중복 나가기 요청입니다. roomId={}, myId={}", roomId, myId);
      return;
    }

    // 내 removed가 원자적으로 확정된 뒤 상대방 상태를 다시 읽는다.
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(roomId, myId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (opponentState.isDeleted()) {      // 상대방도 이미 나감 -> soft delete 표시 (배치가 나중에 아카이브 후 물리삭제)
      // room은 validateChatRoomMember / findAllBy... 로 조회된 managed 엔티티이므로 save로 deletedAt 반영
      room.softDelete();
      chatRoomRepository.save(room);
      log.debug("양쪽 모두 나가 채팅방을 soft delete 표시했습니다. roomId={}", roomId);
    } else {                              // 상대방은 아직 남아있음 -> 시스템 메시지만 전송 (내 상태는 위에서 이미 removed)
      log.debug("상대방이 남아있어 내 상태만 삭제 표시했습니다. roomId={}, myId={}", roomId, myId);
      chatMessageService.sendSystemMessage(room, myId, opponentState);
    }
  }

  /**
   * 관리자 거래 강제 취소 시 채팅방 soft delete 표시 (#750)
   * - 동기 흐름에서는 ChatRoom.deletedAt만 표시하고 실제 물리삭제는 배치가 수행 (PG/Mongo 비원자성 문제 회피)
   * - 존재하지 않는 chatRoomId면 아무것도 하지 않음 (PENDING 상태 등 채팅방 없는 경우 안전 처리)
   */
  @Transactional
  public void adminForceDeleteChatRoom(UUID chatRoomId) {
    ChatRoom room = chatRoomRepository.findById(chatRoomId).orElse(null);
    if (room == null) {
      log.warn("관리자 강제 채팅방 삭제 요청: 존재하지 않는 chatRoomId={}", chatRoomId);
      return;
    }
    room.softDelete();
    chatRoomRepository.save(room);
    log.info("관리자 강제 채팅방 soft delete 완료: chatRoomId={}", chatRoomId);
  }

  /**
   * 실제 물리 삭제 (배치 청소 / 관리자 즉시삭제에서 호출). 멱등.
   * Mongo 먼저, PG 마지막 순서로 삭제 — 중간 실패 시 deletedAt이 남아 다음 배치가 재시도할 수 있게 한다.
   */
  @Transactional
  public void physicalDelete(UUID roomId) {
    // 동시 호출로 이미 삭제된 방이면 건너뛴다 (멱등).
    if (!chatRoomRepository.existsById(roomId)) {
      log.debug("이미 물리 삭제된 채팅방입니다. 건너뜁니다. roomId={}", roomId);
      return;
    }
    chatMessageRepository.deleteByChatRoomId(roomId);
    chatUserStateRepository.deleteAllByChatRoomId(roomId);
    chatRoomRepository.deleteById(roomId);
    log.debug("채팅방 물리 삭제 완료. roomId={}", roomId);
  }

  /**
   * 여러 채팅방의 안 읽은 메시지 수를 단일 집계 쿼리로 계산한다.
   * 반환 Map의 값은 호출부 필터링 규칙과 동일하다.
   * - -1L : 내가 나간(삭제 표시된) 방 → 목록에서 제외 대상
   * -  0L : 현재 접속 중이거나 안 읽은 메시지가 없는 방
   * -   N : 읽음 커서 이후 도착한, 내가 보내지 않은 메시지 수
   *
   * @param memberId 현재 사용자 ID
   * @param chatRoomIds 조회할 채팅방 ID 목록
   * @return Map&lt;채팅방ID, 안 읽은 메시지 수&gt;
   */
  private Map<UUID, Long> getUnreadCounts(UUID memberId, List<UUID> chatRoomIds) {
    // 내 채팅방 상태를 한 번에 조회 (누락된 상태는 호출부의 ensureStates 단계에서 이미 복구됨)
    Map<UUID, ChatUserState> stateByRoomId = chatUserStateRepository
        .findByMemberIdAndChatRoomIdIn(memberId, chatRoomIds).stream()
        .collect(Collectors.toMap(ChatUserState::getChatRoomId, state -> state, (first, second) -> first));

    // 접속 중/삭제/상태없음 방은 메시지 카운트가 필요 없으므로, 읽음 커서가 있는 방만 집계 대상으로 추린다.
    Map<UUID, LocalDateTime> readCursorByRoomId = new HashMap<>();
    Map<UUID, Long> unreadCounts = new HashMap<>();
    for (UUID roomId : chatRoomIds) {
      ChatUserState state = stateByRoomId.get(roomId);
      if (state == null || state.isPresent()) {
        unreadCounts.put(roomId, 0L);
      } else if (state.isDeleted()) {
        unreadCounts.put(roomId, -1L);
      } else {
        readCursorByRoomId.put(roomId, state.getLeftAt());
      }
    }

    // 카운트가 필요한 방들의 안 읽은 메시지 수를 한 번의 집계로 조회한다.
    Map<UUID, Long> aggregatedUnreadCounts = chatMessageRepository.countUnreadMessagesByRoom(readCursorByRoomId, memberId);
    for (UUID roomId : readCursorByRoomId.keySet()) {
      unreadCounts.put(roomId, aggregatedUnreadCounts.getOrDefault(roomId, 0L));
    }
    return unreadCounts;
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
    UUID myItemId;
    if (chatRoom.getTradeReceiver().getMemberId().equals(myMemberId)) {
      targetMemberEntity = chatRoom.getTradeSender();
      chatRoomType = ChatRoomType.RECEIVED;
      // 내가 tradeReceiver → 상대방 물품 = giveItem (tradeSender가 보낸 물품)
      targetItemId = chatRoom.getTradeRequestHistory().getGiveItem().getItemId();
      myItemId = chatRoom.getTradeRequestHistory().getTakeItem().getItemId();
    } else {
      targetMemberEntity = chatRoom.getTradeReceiver();
      chatRoomType = ChatRoomType.REQUESTED;
      // 내가 tradeSender → 상대방 물품 = takeItem (tradeReceiver의 물품)
      targetItemId = chatRoom.getTradeRequestHistory().getTakeItem().getItemId();
      myItemId = chatRoom.getTradeRequestHistory().getGiveItem().getItemId();
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
    String myItemImageUrl = itemImageMap.get(myItemId);

    boolean isBlocked = blockedMemberIds.contains(targetMemberEntity.getMemberId());
    return ChatRoomDetailDto.from(
        roomId,
        isBlocked,
        targetMemberEntity,
        eupMyeonDong,
        unreadCounts.getOrDefault(roomId, 0L),
        content,
        time,
        chatRoomType,
        targetItemImageUrl,
        myItemImageUrl
    );
  }

  // 채팅방 존재 및 멤버 확인
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
