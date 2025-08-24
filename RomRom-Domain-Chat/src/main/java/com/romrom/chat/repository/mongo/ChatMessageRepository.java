package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
  Page<ChatMessage> findByRoomIdOrderByCreatedDateDesc(UUID roomId, Pageable pageable);
  void deleteByRoomId(UUID roomId);
}