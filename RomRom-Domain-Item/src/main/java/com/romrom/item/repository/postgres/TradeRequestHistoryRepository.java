package com.romrom.item.repository.postgres;

import com.romrom.common.constant.TradeStatus;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.TradeRequestHistory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface TradeRequestHistoryRepository extends JpaRepository<TradeRequestHistory, UUID> {

  @Query("SELECT COUNT(t) > 0 FROM TradeRequestHistory t " +
      "WHERE t.tradeStatus IN (com.romrom.common.constant.TradeStatus.PENDING, " +
      "                         com.romrom.common.constant.TradeStatus.CHATTING, " +
      "                         com.romrom.common.constant.TradeStatus.TRADE_COMPLETE_REQUESTED, " +
      "                         com.romrom.common.constant.TradeStatus.TRADED) " +
      "  AND ((t.takeItem.itemId = :takeItemId AND t.giveItem.itemId = :giveItemId) OR (t.takeItem.itemId = :giveItemId AND t.giveItem.itemId = :takeItemId))")
  boolean existsTradeRequestBetweenItems(@Param("takeItemId") UUID takeItemId, @Param("giveItemId") UUID giveItemId);

  Optional<TradeRequestHistory> findByTakeItemAndGiveItem(Item takeItem, Item giveItem);

  // 요청받은 내역 조회 (차단 필터링 추가)
  @Query(
    value = "SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.takeItem = :takeItem " +
      "AND trh.takeItem.itemStatus = com.romrom.common.constant.ItemStatus.AVAILABLE " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member) " +
      "       OR (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member)" +
      ")")
  Page<TradeRequestHistory> findByTakeItem(@Param("takeItem") Item takeItem, Pageable pageable);

  // 요청받은 내역 전체 조회 (AI 추천 정렬용 - 페이징/정렬 없이 전체 로드)
  @Query(
    value = "SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.takeItem = :takeItem " +
      "AND trh.takeItem.itemStatus = com.romrom.common.constant.ItemStatus.AVAILABLE " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member) " +
      "       OR (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member)" +
      ")")
  List<TradeRequestHistory> findAllByTakeItem(@Param("takeItem") Item takeItem);

  Page<TradeRequestHistory> findByGiveItemAndTradeStatus(Item giveItem, TradeStatus tradeStatus, Pageable pageable);

  // 요청한 내역 조회 (차단 필터링 추가)
  @Query("SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.giveItem = :giveItem " +
      "AND trh.giveItem.itemStatus = com.romrom.common.constant.ItemStatus.AVAILABLE " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member) " +
      "       OR (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member)" +
      ")")
  Page<TradeRequestHistory> findByGiveItem(Item giveItem, Pageable pageable);

  // 요청한 내역 전체 조회 (AI 추천 정렬용 - 페이징/정렬 없이 전체 로드)
  @Query("SELECT trh FROM TradeRequestHistory trh " +
      "JOIN FETCH trh.takeItem ti " +
      "JOIN FETCH trh.giveItem gi " +
      "WHERE trh.giveItem = :giveItem " +
      "AND trh.giveItem.itemStatus = com.romrom.common.constant.ItemStatus.AVAILABLE " +
      "AND trh.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND NOT EXISTS (" +
      "    SELECT 1 FROM MemberBlock mb " +
      "    WHERE (mb.blockerMember = trh.giveItem.member AND mb.blockedMember = trh.takeItem.member) " +
      "       OR (mb.blockerMember = trh.takeItem.member AND mb.blockedMember = trh.giveItem.member)" +
      ")")
  List<TradeRequestHistory> findAllByGiveItem(@Param("giveItem") Item giveItem);

  void deleteAllByTakeItemItemId(UUID itemId);

  void deleteAllByGiveItemItemId(UUID itemId);

  /**
   * 알림 발송 대상 회원 수집용: 특정 물품과 관련된 모든 거래 요청 조회 (giveItem/takeItem의 Member 페치 조인)
   * - 관리자 물품 삭제 시 영향받는 회원 ID 수집에 사용
   */
  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member gm " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member tm " +
      "WHERE t.giveItem.itemId = :itemId OR t.takeItem.itemId = :itemId")
  List<TradeRequestHistory> findAllWithMembersByItemId(@Param("itemId") UUID itemId);

  /**
   * FK 위반 방지용: 특정 물품과 관련된 활성 거래 요청을 일괄 CANCELED 처리
   * - TRADED(거래 완료) 상태는 보존, 이미 CANCELED인 것도 제외
   * - 관리자 물품 삭제 시 chat_room FK 제약조건 위반 방지에 사용
   */
  @Modifying
  @Query("UPDATE TradeRequestHistory t SET t.tradeStatus = com.romrom.common.constant.TradeStatus.CANCELED " +
      "WHERE (t.giveItem.itemId = :itemId OR t.takeItem.itemId = :itemId) " +
      "AND t.tradeStatus <> com.romrom.common.constant.TradeStatus.CANCELED " +
      "AND t.tradeStatus <> com.romrom.common.constant.TradeStatus.TRADED")
  void cancelAllActiveByItemId(@Param("itemId") UUID itemId);

  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.takeItem ti " +
      "JOIN FETCH t.giveItem gi " +
      "WHERE t.tradeRequestHistoryId = :tradeRequestHistoryId")
  Optional<TradeRequestHistory> findByTradeRequestHistoryIdWithItems(UUID tradeRequestHistoryId);

  long countByTradeStatusIn(Collection<TradeStatus> statuses);

  /**
   * 관리자용 거래 이력 목록 조회 (상태 필터, 기간 필터, 검색어 지원)
   * - searchKeyword: takeItem/giveItem 물품명, 양쪽 회원 닉네임 대상 LIKE 검색
   */
  @Query(
      value = "SELECT t FROM TradeRequestHistory t " +
              "JOIN FETCH t.takeItem ti JOIN FETCH ti.member tm " +
              "JOIN FETCH t.giveItem gi JOIN FETCH gi.member gm " +
              "WHERE (:tradeStatus IS NULL OR t.tradeStatus = :tradeStatus) " +
              "AND (:startDate IS NULL OR t.createdDate >= :startDate) " +
              "AND (:endDate IS NULL OR t.createdDate <= :endDate) " +
              "AND (:searchKeyword IS NULL OR (" +
              "LOWER(ti.itemName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(gi.itemName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(tm.nickname) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(gm.nickname) LIKE LOWER(CONCAT('%', :searchKeyword, '%'))))",
      countQuery = "SELECT COUNT(t) FROM TradeRequestHistory t " +
              "JOIN t.takeItem ti JOIN ti.member tm " +
              "JOIN t.giveItem gi JOIN gi.member gm " +
              "WHERE (:tradeStatus IS NULL OR t.tradeStatus = :tradeStatus) " +
              "AND (:startDate IS NULL OR t.createdDate >= :startDate) " +
              "AND (:endDate IS NULL OR t.createdDate <= :endDate) " +
              "AND (:searchKeyword IS NULL OR (" +
              "LOWER(ti.itemName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(gi.itemName) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(tm.nickname) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) " +
              "OR LOWER(gm.nickname) LIKE LOWER(CONCAT('%', :searchKeyword, '%'))))"
  )
  Page<TradeRequestHistory> findTradesForAdmin(
      @Param("tradeStatus") TradeStatus tradeStatus,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("searchKeyword") String searchKeyword,
      Pageable pageable
  );

  /**
   * 특정 물품이 포함된 활성 채팅 중인 거래 이력 조회 (교환완료 시스템 메시지 발송용)
   * - CHATTING / TRADE_COMPLETE_REQUESTED 상태만 조회 (이미 완료/취소된 건 제외)
   */
  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member gm " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member tm " +
      "WHERE (t.giveItem.itemId = :itemId OR t.takeItem.itemId = :itemId) " +
      "AND t.tradeStatus IN (com.romrom.common.constant.TradeStatus.CHATTING, " +
      "                      com.romrom.common.constant.TradeStatus.TRADE_COMPLETE_REQUESTED)")
  List<TradeRequestHistory> findActiveChattingHistoriesByItemId(@Param("itemId") UUID itemId);
}
