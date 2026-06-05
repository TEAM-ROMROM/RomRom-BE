package com.romrom.chat.repository.postgres;

import com.romrom.chat.entity.postgres.ChatRoom;
import java.time.LocalDateTime;
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


  // 본인이 속한 채팅방 목록 조회 (soft-delete된 방은 제외)
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender " +
      "JOIN FETCH c.tradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem " +
      "JOIN FETCH trh.giveItem " +
      "WHERE (c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender) " +
      "AND c.deletedAt IS NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c " +
          "WHERE (c.tradeReceiver = :tradeReceiver OR c.tradeSender = :tradeSender) " +
          "AND c.deletedAt IS NULL")
  Page<ChatRoom> findByTradeReceiverOrTradeSender(Member tradeReceiver, Member tradeSender, Pageable pageable);

  // 특정 물품이 포함된 채팅방 조회 (본인이 참여한 채팅방만, soft-delete된 방은 제외)
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender " +
      "JOIN FETCH c.tradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem " +
      "JOIN FETCH trh.giveItem " +
      "WHERE (c.tradeReceiver = :member OR c.tradeSender = :member) " +
      "AND (trh.takeItem.itemId = :itemId OR trh.giveItem.itemId = :itemId) " +
      "AND c.deletedAt IS NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c " +
          "JOIN c.tradeRequestHistory trh " +
          "WHERE (c.tradeReceiver = :member OR c.tradeSender = :member) " +
          "AND (trh.takeItem.itemId = :itemId OR trh.giveItem.itemId = :itemId) " +
          "AND c.deletedAt IS NULL")
  Page<ChatRoom> findByMemberAndItemId(@Param("member") Member member, @Param("itemId") UUID itemId, Pageable pageable);

  @Query("SELECT c.chatRoomId FROM ChatRoom c WHERE c.tradeReceiver.memberId = :id OR c.tradeSender.memberId = :id")
  List<UUID> findAllIdsByMemberId(@Param("id") UUID memberId);

  @Query("SELECT c FROM ChatRoom c WHERE c.tradeSender.memberId = :tradeSenderMemberId OR c.tradeReceiver.memberId = :tradeReceiverMemberId")
  List<ChatRoom> findAllByTradeSender_MemberIdOrTradeReceiver_MemberId(@Param("tradeSenderMemberId") UUID tradeSenderMemberId, @Param("tradeReceiverMemberId") UUID tradeReceiverMemberId);

  // 관리자: soft-delete된(청소 대기) 방 목록 페이지 조회
  @Query(value = "SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.deletedAt IS NOT NULL",
      countQuery = "SELECT count(c) FROM ChatRoom c WHERE c.deletedAt IS NOT NULL")
  Page<ChatRoom> findByDeletedAtIsNotNull(Pageable pageable);

  // 배치: 유예 기간(deletedAt < threshold) 지난 청소 대상 방 조회
  @Query("SELECT c FROM ChatRoom c " +
      "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
      "WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :threshold")
  List<ChatRoom> findCleanupTargets(@Param("threshold") LocalDateTime threshold);
}