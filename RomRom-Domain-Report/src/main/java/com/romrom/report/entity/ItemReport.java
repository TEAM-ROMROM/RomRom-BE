package com.romrom.report.entity;

import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.enums.ItemReportReason;
import com.romrom.report.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class ItemReport extends BasePostgresEntity {

  public static final int EXTRA_COMMENT_MAX_LENGTH = 1000;

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID itemReportId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Item item;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @ElementCollection(fetch = FetchType.LAZY)
  @Enumerated(EnumType.STRING)
  private Set<ItemReportReason> itemReportReasons;


  @Column(length = EXTRA_COMMENT_MAX_LENGTH)
  private String extraComment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private ReportStatus status = ReportStatus.PENDING;
}
