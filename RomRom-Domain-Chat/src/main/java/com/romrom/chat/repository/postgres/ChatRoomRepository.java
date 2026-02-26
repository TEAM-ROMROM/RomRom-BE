package com.romrom.chat.repository.postgres;

import com.romrom.chat.entity.postgres.ChatRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.romrom.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
  @Query("SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.tradeRequestHistory.tradeRequestHistoryId = :tradeRequestHistoryId")
  Optional<ChatRoom> findByTradeRequestHistoryId(UUID tradeRequestHistoryId);

  @Query("SELECT c FROM ChatRoom c JOIN FETCH c.tradeSender JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeRequestHistory WHERE c.chatRoomId = :chatRoomId")
  Optional<ChatRoom> findByChatRoomIdWithSenderAndReceiver(UUID chatRoomId);


  // 본인이 속한 채팅방 목록 조회
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender " +
      "JOIN FETCH c.tradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem " +
      "JOIN FETCH trh.giveItem " +
      "WHERE c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender",
      countQuery = "SELECT count(c) FROM ChatRoom c WHERE c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender")
  Page<ChatRoom> findByTradeReceiverOrTradeSender(Member tradeReceiver, Member tradeSender, Pageable pageable);

  @Query("SELECT c.chatRoomId FROM ChatRoom c WHERE c.tradeReceiver.memberId = :id OR c.tradeSender.memberId = :id")
  List<UUID> findAllIdsByMemberId(@Param("id") UUID memberId);

  List<ChatRoom> findAllByTradeSender_MemberIdAndTradeReceiver_MemberId(UUID tradeSenderMemberId, UUID tradeReceiverMemberId);
}