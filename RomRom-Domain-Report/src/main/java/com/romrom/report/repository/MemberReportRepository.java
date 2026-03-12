package com.romrom.report.repository;

import com.romrom.member.entity.Member;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberReportRepository extends JpaRepository<MemberReport, UUID> {
  boolean existsByTargetMemberAndReporter(Member targetMember, Member reporter);

  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  Page<MemberReport> findAllByOrderByCreatedDateDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  Page<MemberReport> findByStatusOrderByCreatedDateDesc(ReportStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  Optional<MemberReport> findByMemberReportId(UUID memberReportId);

  long countByStatus(ReportStatus status);

  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  List<MemberReport> findByTargetMemberOrderByCreatedDateDesc(Member targetMember);

  long countByTargetMember(Member targetMember);
}
