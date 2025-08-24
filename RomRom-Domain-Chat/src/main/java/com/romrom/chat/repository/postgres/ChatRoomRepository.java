package com.romrom.chat.repository.postgres;

import com.romrom.chat.entity.postgres.ChatRoom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
  Optional<ChatRoom> findByMemberAAndMemberB(UUID memberA, UUID memberB);
  Optional<ChatRoom> findByRoomId(UUID roomId);
  boolean existsByRoomId(UUID roomId);
}