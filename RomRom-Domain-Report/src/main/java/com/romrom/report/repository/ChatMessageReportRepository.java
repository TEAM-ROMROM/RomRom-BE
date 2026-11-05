package com.romrom.report.repository;

import com.romrom.member.entity.Member;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.enums.ReportStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageReportRepository extends JpaRepository<ChatMessageReport, UUID> {

  boolean existsByChatMessageIdAndReporterMemberId(String chatMessageId, UUID reporterMemberId);

  // ===== 관리자 채팅 신고 탭 (#873, 신고자·대상 회원·사유 함께 조회) =====

  @EntityGraph(attributePaths = {"reporter", "targetMember", "reasons"})
  Page<ChatMessageReport> findAllByOrderByCreatedDateDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"reporter", "targetMember", "reasons"})
  Page<ChatMessageReport> findByStatusOrderByCreatedDateDesc(ReportStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"reporter", "targetMember", "reasons"})
  Optional<ChatMessageReport> findByChatMessageReportId(UUID chatMessageReportId);

  long countByStatus(ReportStatus status);

  long countByTargetMember(Member targetMember);
}
