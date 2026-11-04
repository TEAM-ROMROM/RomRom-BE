package com.romrom.report.repository;

import com.romrom.report.entity.ChatMessageReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageReportRepository extends JpaRepository<ChatMessageReport, UUID> {

  boolean existsByChatMessageIdAndReporterMemberId(String chatMessageId, UUID reporterMemberId);
}
