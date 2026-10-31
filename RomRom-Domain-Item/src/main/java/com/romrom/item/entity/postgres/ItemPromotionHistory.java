package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 롬업(우선 노출) 활성화 이력 (#819): 물품 24시간 1회·회원 하루 3회 한도 판정 원본
 */
@Entity
@Table(indexes = {
    @Index(name = "idx_item_promotion_history_member_started", columnList = "member_member_id, promotionStartedAt"),
    @Index(name = "idx_item_promotion_history_item_started", columnList = "item_item_id, promotionStartedAt")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true, exclude = {"member", "item"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ItemPromotionHistory extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID itemPromotionHistoryId;

  // 활성화한 회원 (물품 소유자)
  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY)
  private Item item;

  @Column(nullable = false)
  private LocalDateTime promotionStartedAt;

  @Column(nullable = false)
  private LocalDateTime promotionExpiresAt;
}
