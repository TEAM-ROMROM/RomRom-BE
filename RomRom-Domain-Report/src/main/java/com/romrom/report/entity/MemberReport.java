package com.romrom.report.entity;

import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import com.romrom.report.enums.MemberReportReason;
import com.romrom.report.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class MemberReport extends BasePostgresEntity {

  public static final int EXTRA_COMMENT_MAX_LENGTH = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID memberReportId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member targetMember;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member reporter;

  @ElementCollection(fetch = FetchType.LAZY)
  @Enumerated(EnumType.STRING)
  private Set<MemberReportReason> memberReportReasons;

  @Column(length = EXTRA_COMMENT_MAX_LENGTH)
  private String extraComment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ReportStatus status = ReportStatus.PENDING;
}
