package com.romrom.report.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.enums.ReportStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채팅 메시지 신고 (#860): 신고자, 대상 회원, 채팅방·메시지 ID, 신고 시점 본문 사본, 사유(회원 신고 사유 재사용)
 * 테이블 원본: Flyway V1_4_71
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ChatMessageReport extends BasePostgresEntity {

  public static final int EXTRA_COMMENT_MAX_LENGTH = 1000;
  // 신고 시점 메시지 본문 사본 최대 길이
  public static final int MESSAGE_CONTENT_SNAPSHOT_MAX_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID chatMessageReportId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member reporter;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member targetMember;

  @Column(nullable = false)
  private UUID chatRoomId;

  // Mongo ChatMessage ID
  @Column(nullable = false, length = 64)
  private String chatMessageId;

  @Column(length = MESSAGE_CONTENT_SNAPSHOT_MAX_LENGTH)
  private String messageContentSnapshot;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "chat_message_report_reason", joinColumns = @JoinColumn(name = "chat_message_report_id"))
  @Column(name = "reason", nullable = false)
  @Enumerated(EnumType.STRING)
  private Set<MemberReportReason> reasons;

  @Column(length = EXTRA_COMMENT_MAX_LENGTH)
  private String extraComment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ReportStatus status = ReportStatus.PENDING;
}
