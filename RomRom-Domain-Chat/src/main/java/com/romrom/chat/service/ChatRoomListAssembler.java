package com.romrom.chat.service;

import com.romrom.chat.dto.ChatRoomDetailDto;
import com.romrom.chat.dto.ChatRoomResponse;
import com.romrom.chat.dto.ChatRoomType;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatMessageRepository;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberBlock;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.service.MemberBlockService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Component;

/**
 * 채팅방 목록 조립: 벌크 조회(상태·안 읽음·최근 메시지·위치·차단·물품 이미지·알림 끄기) > 나간 방 제외 > DTO 변환
 * 호출 트랜잭션 안에서 사용 (ChatRoomService 목록 조회 2곳)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatRoomListAssembler {

  private final ChatMessageRepository chatMessageRepository;
  private final ChatUserStateRepository chatUserStateRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberBlockService memberBlockService;
  private final ItemImageRepository itemImageRepository;
  private final ChatUserStateEnsureService chatUserStateEnsureService;

  /**
   * ChatRoom 슬라이스로부터 벌크 데이터를 조회하고, 삭제/나간 채팅방을 필터링한 뒤
   * ChatRoomDetailDto 목록을 조립하여 ChatRoomResponse를 반환하는 공통 로직
   */
  public ChatRoomResponse assemble(Slice<ChatRoom> chatRoomsSlice, UUID myMemberId, Pageable pageable) {
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

    // 내 방 상태 1회 조회 > 안 읽음 수·알림 끄기 공통
    Map<UUID, ChatUserState> myStateByRoomId = chatUserStateRepository
        .findByMemberIdAndChatRoomIdIn(myMemberId, chatRoomIds).stream()
        .collect(Collectors.toMap(ChatUserState::getChatRoomId, state -> state, (first, second) -> first));
    Map<UUID, Long> unreadCounts = getUnreadCounts(myMemberId, chatRoomIds, myStateByRoomId);
    Set<UUID> mutedChatRoomIds = myStateByRoomId.values().stream()
        .filter(ChatUserState::isNotificationMuted)
        .map(ChatUserState::getChatRoomId)
        .collect(Collectors.toSet());
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
        .map(chatRoom -> convertToDetailDto(chatRoom, myMemberId, unreadCounts, latestMessageMap, locationMap, blockedMemberIds, itemImageMap,
            mutedChatRoomIds))
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
  private Map<UUID, Long> getUnreadCounts(UUID memberId, List<UUID> chatRoomIds, Map<UUID, ChatUserState> stateByRoomId) {
    // 내 채팅방 상태는 호출부에서 한 번에 조회 (누락된 상태는 ensureStates 단계에서 이미 복구됨)

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
    // 목록 미리보기도 관리자 숨김·보낸 사람 삭제 메시지 마스킹
    latestMessages.forEach(ChatMessage::maskForMemberView);
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
                                               Set<UUID> blockedMemberIds, Map<UUID, String> itemImageMap,
                                               Set<UUID> mutedChatRoomIds) {
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
        myItemImageUrl,
        mutedChatRoomIds.contains(roomId)
    );
  }
}
