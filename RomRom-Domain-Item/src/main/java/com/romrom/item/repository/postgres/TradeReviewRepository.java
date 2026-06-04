package com.romrom.item.repository.postgres;

import com.romrom.common.constant.TradeReviewRating;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.entity.postgres.TradeReview;
import com.romrom.member.entity.Member;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeReviewRepository extends JpaRepository<TradeReview, UUID> {

  // 동일 거래에 같은 작성자의 후기가 이미 존재하는지 확인
  boolean existsByTradeRequestHistoryAndReviewerMember(
      TradeRequestHistory tradeRequestHistory, Member reviewerMember);

  // reviewedMember의 memberId로 받은 후기 페이지 조회
  Page<TradeReview> findByReviewedMember_MemberId(UUID memberId, Pageable pageable);

  // 관리자 대시보드용: 기간 내 작성 후기 수 (startDate/endDate null이면 전체)
  @Query(
      "SELECT COUNT(r) FROM TradeReview r " +
      "WHERE (:startDate IS NULL OR r.createdDate >= :startDate) " +
      "AND (:endDate IS NULL OR r.createdDate <= :endDate)")
  long countByCreatedDateBetweenNullable(
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate);

  /**
   * 관리자 후기 관리용 목록 조회 (평점/기간/블라인드여부 필터, 페이지네이션)
   * - tradeReviewRating / isBlindedFilter 가 null 이면 해당 필터 미적용
   * - 작성자/대상자/거래를 페치 조인해 deep-link 정보 노출
   */
  @Query(
      value = "SELECT r FROM TradeReview r " +
              "JOIN FETCH r.reviewerMember " +
              "JOIN FETCH r.reviewedMember " +
              "JOIN FETCH r.tradeRequestHistory " +
              "WHERE (:tradeReviewRating IS NULL OR r.tradeReviewRating = :tradeReviewRating) " +
              "AND (:startDate IS NULL OR r.createdDate >= :startDate) " +
              "AND (:endDate IS NULL OR r.createdDate <= :endDate) " +
              "AND (:isBlindedFilter IS NULL OR r.blindInfo.isBlinded = :isBlindedFilter)",
      countQuery = "SELECT COUNT(r) FROM TradeReview r " +
              "WHERE (:tradeReviewRating IS NULL OR r.tradeReviewRating = :tradeReviewRating) " +
              "AND (:startDate IS NULL OR r.createdDate >= :startDate) " +
              "AND (:endDate IS NULL OR r.createdDate <= :endDate) " +
              "AND (:isBlindedFilter IS NULL OR r.blindInfo.isBlinded = :isBlindedFilter)")
  Page<TradeReview> findReviewsForAdmin(
      @Param("tradeReviewRating") TradeReviewRating tradeReviewRating,
      @Param("startDate") LocalDateTime startDate,
      @Param("endDate") LocalDateTime endDate,
      @Param("isBlindedFilter") Boolean isBlindedFilter,
      Pageable pageable);
}
