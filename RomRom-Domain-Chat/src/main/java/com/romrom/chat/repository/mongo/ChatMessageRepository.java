package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
  List<ChatMessage> findTop50ByRoomIdOrderBySentAtDesc(String roomId);
}