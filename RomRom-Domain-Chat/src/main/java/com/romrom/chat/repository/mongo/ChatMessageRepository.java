package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
  Page<ChatMessage> findByChatRoomIdOrderByCreatedDateDesc(UUID chatRoomId, Pageable pageable);
  void deleteByChatRoomId(UUID chatRoomId);
}