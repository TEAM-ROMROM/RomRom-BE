package com.romrom.chat.service;

import com.romrom.chat.entity.mongo.ChatUserState;
import com.romrom.chat.entity.postgres.ChatRoom;
import com.romrom.chat.repository.mongo.ChatUserStateRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
// 오래된 데이터나 예외 케이스로 ChatUserState가 비어 있는 방을 조회 시점에 자동 복구한다.
public class ChatUserStateEnsureService {

  private final ChatUserStateRepository chatUserStateRepository;

  // 여러 채팅방을 한 번에 스캔해서, 참여자 2명의 상태 문서가 모두 존재하도록 맞춘다.
  @Transactional
  public void ensureStates(List<ChatRoom> rooms) {
    if (rooms == null || rooms.isEmpty()) {
      return;
    }

    List<UUID> chatRoomIds = rooms.stream()
        .map(ChatRoom::getChatRoomId)
        .distinct()
        .toList();

    List<ChatUserState> existingStates = chatUserStateRepository.findByChatRoomIdIn(chatRoomIds);
    Map<UUID, Set<UUID>> existingMemberIdsByRoomId = new HashMap<>();
    for (ChatUserState existingState : existingStates) {
      existingMemberIdsByRoomId
          .computeIfAbsent(existingState.getChatRoomId(), ignored -> new HashSet<>())
          .add(existingState.getMemberId());
    }

    List<ChatUserState> statesToCreate = new ArrayList<>();
    for (ChatRoom room : rooms) {
      Set<UUID> existingMemberIds = existingMemberIdsByRoomId
          .computeIfAbsent(room.getChatRoomId(), ignored -> new HashSet<>());

      addMissingState(room.getChatRoomId(), room.getTradeSender().getMemberId(), existingMemberIds, statesToCreate);
      addMissingState(room.getChatRoomId(), room.getTradeReceiver().getMemberId(), existingMemberIds, statesToCreate);
    }

    if (statesToCreate.isEmpty()) {
      return;
    }

    try {
      chatUserStateRepository.saveAll(statesToCreate);
      log.warn("누락된 ChatUserState를 자동 복구했습니다. count={}, roomIds={}", statesToCreate.size(), chatRoomIds);
    } catch (DuplicateKeyException duplicateKeyException) {
      // 동시 요청에서 같은 state를 동시에 만들 수 있으므로 unique 충돌은 복구 성공으로 보고 넘긴다.
      log.debug("ChatUserState 자동 복구 중 중복 생성이 감지되었습니다. 동시 요청으로 보고 무시합니다. roomIds={}", chatRoomIds);
    }
  }

  // 이미 존재하는 memberId는 건너뛰고, 빠진 조합만 생성 목록에 추가한다.
  private void addMissingState(UUID chatRoomId, UUID memberId, Set<UUID> existingMemberIds, List<ChatUserState> statesToCreate) {
    if (existingMemberIds.contains(memberId)) {
      return;
    }
    existingMemberIds.add(memberId);
    statesToCreate.add(ChatUserState.create(chatRoomId, memberId));
  }
}
