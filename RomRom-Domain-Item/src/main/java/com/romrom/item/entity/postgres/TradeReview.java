package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.constant.TradeReviewTag;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.common.entity.postgres.BlindInfo;
import com.romrom.common.entity.postgres.Blindable;
import com.romrom.member.entity.Member;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TradeReview extends BasePostgresEntity implements Blindable {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID tradeReviewId;

  @ManyToOne(fetch = FetchType.LAZY)
  private TradeRequestHistory tradeRequestHistory;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member reviewerMember; // 후기 작성자

  @ManyToOne(fetch = FetchType.LAZY)
  private Member reviewedMember; // 후기 받는 사람

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TradeReviewRating tradeReviewRating; // 종합 평가

  @ElementCollection
  private List<TradeReviewTag> tradeReviewTags; // 세부 항목

  @Column(length = 200)
  private String reviewComment; // 한마디

  // 관리자 블라인드(비공개) 처리 정보 (isBlinded/사유/처리자/시각)
  @Embedded
  @Builder.Default
  private BlindInfo blindInfo = new BlindInfo();

  @Override
  public void blind(String blindReason, UUID blindByAdminId) {
    if (this.blindInfo == null) {
      this.blindInfo = new BlindInfo();
    }
    this.blindInfo.setIsBlinded(true);
    this.blindInfo.setBlindReason(blindReason);
    this.blindInfo.setBlindByAdminId(blindByAdminId);
    this.blindInfo.setBlindDate(LocalDateTime.now());
  }

  @Override
  public void unblind() {
    if (this.blindInfo == null) {
      this.blindInfo = new BlindInfo();
    }
    this.blindInfo.setIsBlinded(false);
    this.blindInfo.setBlindReason(null);
    this.blindInfo.setBlindByAdminId(null);
    this.blindInfo.setBlindDate(null);
  }
}
