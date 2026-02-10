package com.romrom.report.repository;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemReportRepository extends JpaRepository<ItemReport, UUID> {
  boolean existsByItemAndMember(Item item, Member member);

  @EntityGraph(attributePaths = {"item", "member", "itemReportReasons"})
  Page<ItemReport> findAllByOrderByCreatedDateDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"item", "member", "itemReportReasons"})
  Page<ItemReport> findByStatusOrderByCreatedDateDesc(ReportStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"item", "member", "itemReportReasons"})
  Optional<ItemReport> findByItemReportId(UUID itemReportId);

  long countByStatus(ReportStatus status);
}

