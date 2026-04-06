package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.constant.TradeReviewTag;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import jakarta.persistence.*;
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
public class TradeReview extends BasePostgresEntity {

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
}
