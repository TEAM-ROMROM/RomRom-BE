package com.romrom.item.entity.postgres;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.converter.ProductCategoryConverter;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import com.romrom.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UserInteractionScore extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID userInteractionScoreId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Member member;

  @Convert(converter = ProductCategoryConverter.class)
  private ItemCategory itemCategory;

  @Builder.Default
  private Integer likeCount = 0;

  @Builder.Default
  private Integer viewCount = 0;

  private Double totalScore;

  // 점수 업데이트
  public void updateScore(double likeWeight, double viewWeight) {
    this.totalScore = (this.likeCount * likeWeight) + (this.viewCount * viewWeight);
  }
}