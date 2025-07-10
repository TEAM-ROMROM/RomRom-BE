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
    name = "report",
    uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "member_id"})
)
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class Report extends BasePostgresEntity {

  @Id
  @GeneratedValue(generator = "uuid2")
  @Column(name = "report_id")
  private UUID reportId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "item_id")
  private Item item;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "report_reason", joinColumns = @JoinColumn(name = "member_id"))
  @Column(name = "report_reason")
  @Enumerated(EnumType.STRING)
  private Set<ReportReason> reportReasons;

  @Column(length = 300)
  private String extraComment;
}
