package com.romrom.item.repository.postgres;

import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.romrom.item.entity.postgres.ItemPromotionHistory;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPromotionHistoryRepository extends JpaRepository<ItemPromotionHistory, UUID> {

  // 물품 한도: 기준 시각 이후 활성화 이력 존재 여부
  boolean existsByItemItemIdAndPromotionStartedAtAfter(UUID itemId, LocalDateTime promotionStartedAfter);

  // 회원 한도: 기준 시각(KST 오늘 0시) 이후 활성화 횟수
  long countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(UUID memberId, LocalDateTime promotionStartedFrom);

  // 관리자: 물품 롬업 이력 (최신순, 실행 회원 포함)
  @Query("SELECT h FROM ItemPromotionHistory h JOIN FETCH h.member WHERE h.item.itemId = :itemId ORDER BY h.promotionStartedAt DESC")
  List<ItemPromotionHistory> findAllByItemIdForAdmin(@Param("itemId") UUID itemId);
}
