package com.romrom.chat.repository.postgres;

import com.romrom.chat.entity.postgres.ChatRoom;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
  Optional<ChatRoom> findByMemberAAndMemberB(UUID memberA, UUID memberB);
  Optional<ChatRoom> findByChatRoomId(UUID roomId);
  boolean existsByChatRoomId(UUID roomId);
  Page<ChatRoom> findByMemberAOrMemberB(UUID memberA, UUID memberB, Pageable pageable);
}