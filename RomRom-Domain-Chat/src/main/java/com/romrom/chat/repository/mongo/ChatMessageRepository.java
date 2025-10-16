package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
  Page<ChatMessage> findByChatRoomIdOrderByCreatedDateDesc(UUID chatRoomId, Pageable pageable);
  ChatMessage findTopByChatRoomIdAndSenderIdNotOrderByCreatedDateDesc(UUID chatRoomId, UUID senderId);
  void deleteByChatRoomId(UUID chatRoomId);
  long countByChatRoomId(UUID chatRoomId);
  long countByChatRoomIdAndCreatedDateAfterAndSenderIdNot(UUID chatRoomId, LocalDateTime createdDate, UUID senderId);
  // 특정 시간 이후에 온 메시지의 개수를 세는 메서드
  long countByChatRoomIdAndCreatedDateAfter(UUID chatRoomId, Instant createdDate);
}