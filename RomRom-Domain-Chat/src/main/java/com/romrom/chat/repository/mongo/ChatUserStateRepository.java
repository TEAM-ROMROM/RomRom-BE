package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatUserState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatUserStateRepository extends MongoRepository<ChatUserState, String> {
    Optional<ChatUserState> findByChatRoomIdAndMemberId(UUID chatRoomId, UUID memberId);
    List<ChatUserState> findByMemberIdAndChatRoomIdIn(UUID memberId, List<UUID> chatRoomIds);
    void deleteAllByChatRoomId(UUID chatRoomId);

    void deleteAllByChatRoomIdIn(List<UUID> chatRoomIds);
}