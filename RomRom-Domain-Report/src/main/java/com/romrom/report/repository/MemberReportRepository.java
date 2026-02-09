package com.romrom.report.repository;

import com.romrom.member.entity.Member;
import com.romrom.report.entity.MemberReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MemberReportRepository extends JpaRepository<MemberReport, UUID> {
  boolean existsByTargetMemberAndReporter(Member targetMember, Member reporter);
}
