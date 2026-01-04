package com.romrom.member.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(indexes = {
    @Index(name = "idx_member_block_composite", columnList = "blocker_member_id, blocked_member_id")
})
public class MemberBlock extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID memberBlockId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "blocker_member_id", nullable = false) // 컬럼명 명시
  private Member blockerMember;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "blocked_member_id", nullable = false) // 컬럼명 명시
  private Member blockedMember;
}