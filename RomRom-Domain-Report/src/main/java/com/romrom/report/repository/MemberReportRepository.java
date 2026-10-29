package com.romrom.report.repository;

import com.romrom.common.dto.GroupCountProjection;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  // === Admin 360 View 전용 ===
  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  Page<MemberReport> findByTargetMemberOrderByCreatedDateDesc(Member targetMember, Pageable pageable);

  @EntityGraph(attributePaths = {"targetMember", "reporter", "memberReportReasons"})
  Page<MemberReport> findByReporterOrderByCreatedDateDesc(Member reporterMember, Pageable pageable);

  long countByReporter(Member reporterMember);

  // ===== 관리자 대시보드 분석 (#827) =====

  /**
   * 처리 상태별 회원 신고 수 (기간 내 접수)
   */
  @Query(value = "SELECT status AS group_key, COUNT(*) AS count FROM member_report "
      + "WHERE created_date BETWEEN :startDateTime AND :endDateTime GROUP BY status", nativeQuery = true)
  List<GroupCountProjection> countGroupedByStatusBetween(
      @Param("startDateTime") LocalDateTime startDateTime, @Param("endDateTime") LocalDateTime endDateTime);
}
