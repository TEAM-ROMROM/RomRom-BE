package com.romrom.item.repository.postgres;

import com.romrom.item.entity.postgres.ItemPromotionHistory;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPromotionHistoryRepository extends JpaRepository<ItemPromotionHistory, UUID> {

  // 물품 한도: 기준 시각 이후 활성화 이력 존재 여부
  boolean existsByItemItemIdAndPromotionStartedAtAfter(UUID itemId, LocalDateTime promotionStartedAfter);

  // 회원 한도: 기준 시각(KST 오늘 0시) 이후 활성화 횟수
  long countByMemberMemberIdAndPromotionStartedAtGreaterThanEqual(UUID memberId, LocalDateTime promotionStartedFrom);
}
