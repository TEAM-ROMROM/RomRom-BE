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
              "AND (CAST(:startDate AS timestamp) IS NULL OR t.createdDate >= :startDate) " +
              "AND (CAST(:endDate AS timestamp) IS NULL OR t.createdDate <= :endDate) " +
              "AND (:searchKeyword IS NULL OR (" +
              "LOWER(ti.itemName) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(gi.itemName) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(tm.nickname) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(gm.nickname) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%'))))",
      countQuery = "SELECT COUNT(t) FROM TradeRequestHistory t " +
              "JOIN t.takeItem ti JOIN ti.member tm " +
              "JOIN t.giveItem gi JOIN gi.member gm " +
              "WHERE (:tradeStatus IS NULL OR t.tradeStatus = :tradeStatus) " +
              "AND (CAST(:startDate AS timestamp) IS NULL OR t.createdDate >= :startDate) " +
              "AND (CAST(:endDate AS timestamp) IS NULL OR t.createdDate <= :endDate) " +
              "AND (:searchKeyword IS NULL OR (" +
              "LOWER(ti.itemName) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(gi.itemName) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(tm.nickname) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%')) " +
              "OR LOWER(gm.nickname) LIKE LOWER(CONCAT('%', CAST(:searchKeyword AS string), '%'))))"
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

  /**
   * 관리자 대시보드용: 거래 상태별 건수를 단일 group by 집계로 반환 (기간 옵션)
   * - startDate/endDate 가 null 이면 전체 기간
   * - 상태마다 쿼리를 분기하지 않고 한 번에 집계 (데이터 주도 카드 렌더용)
   */
  // CAST(:param AS timestamp): 두 날짜가 모두 null일 때 PostgreSQL이 파라미터 타입을 추론 못해
  // "could not determine data type of parameter" 로 깨지는 문제 방지 (IS NULL 절에 명시 타입 부여)
  @Query("SELECT t.tradeStatus AS tradeStatus, COUNT(t) AS count FROM TradeRequestHistory t " +
      "WHERE (CAST(:startDate AS timestamp) IS NULL OR t.createdDate >= :startDate) " +
      "AND (CAST(:endDate AS timestamp) IS NULL OR t.createdDate <= :endDate) " +
      "GROUP BY t.tradeStatus")
  List<TradeStatusCountProjection> countGroupedByTradeStatus(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  /**
   * 관리자 대시보드용: 최근 교환완료(TRADED) 거래 N건 (양쪽 물품/회원 페치 조인)
   */
  @Query("SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member tm " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member gm " +
      "WHERE t.tradeStatus = com.romrom.common.constant.TradeStatus.TRADED " +
      "ORDER BY t.updatedDate DESC")
  List<TradeRequestHistory> findRecentTradedForAdmin(Pageable pageable);

  /**
   * 거래 상태별 집계 projection (status + count)
   */
  interface TradeStatusCountProjection {
    TradeStatus getTradeStatus();
    Long getCount();
  }

  // === Admin 360 View 전용: 회원 양쪽(give/take) 거래 메서드 ===

  // 회원이 giveItem 측인 거래(거래 요청자)
  @Query(value = "SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member " +
      "WHERE t.giveItem.member.memberId = :memberId",
      countQuery = "SELECT count(t) FROM TradeRequestHistory t WHERE t.giveItem.member.memberId = :memberId")
  Page<TradeRequestHistory> findByGiveItemMemberId(@Param("memberId") UUID memberId, Pageable pageable);

  // 회원이 takeItem 측인 거래(거래 수신자)
  @Query(value = "SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member " +
      "WHERE t.takeItem.member.memberId = :memberId",
      countQuery = "SELECT count(t) FROM TradeRequestHistory t WHERE t.takeItem.member.memberId = :memberId")
  Page<TradeRequestHistory> findByTakeItemMemberId(@Param("memberId") UUID memberId, Pageable pageable);

  // 회원 양쪽 합산 (BOTH)
  @Query(value = "SELECT t FROM TradeRequestHistory t " +
      "JOIN FETCH t.giveItem gi JOIN FETCH gi.member " +
      "JOIN FETCH t.takeItem ti JOIN FETCH ti.member " +
      "WHERE t.giveItem.member.memberId = :memberId OR t.takeItem.member.memberId = :memberId",
      countQuery = "SELECT count(t) FROM TradeRequestHistory t " +
          "WHERE t.giveItem.member.memberId = :memberId OR t.takeItem.member.memberId = :memberId")
  Page<TradeRequestHistory> findByMemberIdEitherSide(@Param("memberId") UUID memberId, Pageable pageable);

  long countByGiveItemMemberMemberId(UUID memberId);

  long countByTakeItemMemberMemberId(UUID memberId);

  @Query("SELECT count(t) FROM TradeRequestHistory t " +
      "WHERE (t.giveItem.member.memberId = :memberId OR t.takeItem.member.memberId = :memberId)")
  long countByMemberIdEitherSide(@Param("memberId") UUID memberId);

  @Query("SELECT count(t) FROM TradeRequestHistory t " +
      "WHERE (t.giveItem.member.memberId = :memberId OR t.takeItem.member.memberId = :memberId) " +
      "AND t.tradeStatus = :tradeStatus")
  long countByMemberIdEitherSideAndTradeStatus(@Param("memberId") UUID memberId, @Param("tradeStatus") TradeStatus tradeStatus);

  // 진행중 거래 일괄 CANCELED (FORCE_WITHDRAW용)
  @Modifying
  @Query("UPDATE TradeRequestHistory t SET t.tradeStatus = com.romrom.common.constant.TradeStatus.CANCELED " +
      "WHERE (t.giveItem.member.memberId = :memberId OR t.takeItem.member.memberId = :memberId) " +
      "AND t.tradeStatus IN (com.romrom.common.constant.TradeStatus.PENDING, " +
      "                       com.romrom.common.constant.TradeStatus.CHATTING, " +
      "                       com.romrom.common.constant.TradeStatus.TRADE_COMPLETE_REQUESTED)")
  int cancelAllOngoingByMemberId(@Param("memberId") UUID memberId);
}
