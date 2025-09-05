package com.romrom.chat.repository.postgres;

import com.romrom.chat.entity.postgres.ChatRoom;
import java.util.Optional;
import java.util.UUID;

import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
  @Query("SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.tradeRequestHistory = :tradeRequestHistory")
  Optional<ChatRoom> findByTradeRequestHistory(TradeRequestHistory tradeRequestHistory);

  @Query("SELECT c FROM ChatRoom c JOIN FETCH c.tradeRequestHistory WHERE c.chatRoomId = :chatRoomId")
  Optional<ChatRoom> findByChatRoomId(UUID chatRoomId);

  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender",
      countQuery = "SELECT count(c) FROM ChatRoom c WHERE c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender")
  Page<ChatRoom> findByTradeReceiverOrTradeSender(Member tradeReceiver, Member tradeSender, Pageable pageable);
}