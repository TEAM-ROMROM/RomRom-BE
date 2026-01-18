package com.romrom.item.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "recommendation")
@Getter
@Setter
public class RecommendationConfig {

  private Weight weight;
  private double likeMultiplier;    // 좋아요 배수 (5.0)
  private double timeDecayLambda;   // 시간 감쇠 계수 (0.1)

  @Getter
  @Setter
  public static class Weight {
    private double category;      // 최종 점수 내 카테고리 가중치 (0.6)
    private double freshness;     // 최종 점수 내 신선도 가중치 (0.4)
    private double explicit;      // 카테고리 점수 내 명시적 가중치 (0.4)
    private double implicit;      // 카테고리 점수 내 행동 기반 가중치 (0.6)
  }
}
