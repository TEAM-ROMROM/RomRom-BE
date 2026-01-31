package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
  Page<ChatMessage> findByChatRoomIdOrderByCreatedDateDesc(UUID chatRoomId, Pageable pageable);
  Optional<ChatMessage> findTopByChatRoomIdAndSenderIdNotOrderByCreatedDateDesc(UUID chatRoomId, UUID senderId);
  void deleteByChatRoomId(UUID chatRoomId);
  // 특정 senderId(나)가 보낸 메시지가 아니면서 특정 시간 이후에 온 메시지의 개수를 세는 메서드
  long countByChatRoomIdAndCreatedDateAfterAndSenderIdNot(UUID chatRoomId, LocalDateTime createdDate, UUID senderId);

  /**
   * (N+1 방지용 배치 쿼리)
   * 주어진 채팅방 ID 목록(chatRoomIds)에 대해,
   * 각 채팅방의 가장 최신 메시지 1개씩을 조회합니다.
   *
   * @param chatRoomIds 조회할 채팅방 ID 리스트
   * @return 각 채팅방의 가장 최신 ChatMessage 리스트
   */
  @Aggregation(pipeline = {
      "{ '$match': { 'chatRoomId': { '$in': ?0 } } }", // 1. ID 목록으로 필터링
      "{ '$sort': { 'createdDate': -1 } }",           // 2. 최신순 정렬
      "{ '$group': { " +                         // 3. chatRoomId로 그룹화
          "'_id': '$chatRoomId', " +
          "'lastMessage': { '$first': '$$ROOT' } " + // 4. 각 그룹의 첫 번째(가장 최신) 문서 선택
          "}}",
      "{ '$replaceRoot': { 'newRoot': '$lastMessage' } }" // 5. 루트 문서를 lastMessage로 교체
  })
  List<ChatMessage> findLatestMessageForChatRooms(List<UUID> chatRoomIds);

  void deleteByChatRoomIdIn(List<UUID> chatRoomIds);
}