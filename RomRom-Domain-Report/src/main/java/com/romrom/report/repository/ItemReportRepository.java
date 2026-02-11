package com.romrom.report.repository;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ItemReport;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemReportRepository extends JpaRepository<ItemReport, UUID> {
  boolean existsByItemAndMember(Item item, Member member);

  // 단일 물품 신고 여부 확인
  boolean existsByItemItemIdAndMemberMemberId(UUID itemId, UUID memberId);

  // 배치 물품 신고 여부 확인
  @Query("SELECT ir.item.itemId FROM ItemReport ir WHERE ir.member.memberId = :memberId AND ir.item.itemId IN :itemIds")
  Set<UUID> findReportedItemIdsByMemberIdAndItemIds(@Param("memberId") UUID memberId, @Param("itemIds") Collection<UUID> itemIds);
}

