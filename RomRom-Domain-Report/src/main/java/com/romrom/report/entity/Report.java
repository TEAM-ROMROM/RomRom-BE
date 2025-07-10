package com.romrom.report.entity;

import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import com.romrom.report.enums.ReportReason;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "member_id"})
)
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Report extends BasePostgresEntity {

  public static final int EXTRA_COMMENT_MAX_LENGTH = 1000;

  @Id
  private UUID reportId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Item item;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "report_reason", joinColumns = @JoinColumn(name = "member_id"))
  @Enumerated(EnumType.STRING)
  private Set<ReportReason> reportReasons;

  @Column(length = EXTRA_COMMENT_MAX_LENGTH)
  private String extraComment;
}
