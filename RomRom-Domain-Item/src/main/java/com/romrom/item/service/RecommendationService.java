package com.romrom.item.service;

import com.romrom.item.config.RecommendationConfig;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

  private final RecommendationConfig config;

  /**
   * 신선도 점수 = e^(-λ × 경과일수)
   */
  public double calculateFreshnessScore(LocalDateTime createdDate) {
    long daysDiff = ChronoUnit.DAYS.between(createdDate, LocalDateTime.now());
    return Math.exp(-config.getTimeDecayLambda() * daysDiff);
  }

  /**
   * 카테고리 선호도 = (명시적 점수 * 가중치) + (정규화된 행동 점수 × 가중치)
   */
  public double combineCategoryScore(boolean isExplicitPreferred, double normalizedImplicitScore) {
    double explicitScore = isExplicitPreferred ? config.getWeight().getExplicit() : 0.0;
    double implicitWeight = config.getWeight().getImplicit();

    return explicitScore + (normalizedImplicitScore * implicitWeight);
  }

  /**
   * 최종 점수 = (카테고리 선호도 × 가중치) + (신선도 × 가중치)
   */
  public double calculateFinalScore(double categoryScore, double freshnessScore) {
    double catWeight = config.getWeight().getCategory();
    double freshWeight = config.getWeight().getFreshness();

    return (categoryScore * catWeight) + (freshnessScore * freshWeight);
  }
}
