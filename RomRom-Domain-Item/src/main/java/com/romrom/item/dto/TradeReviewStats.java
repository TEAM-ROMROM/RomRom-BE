package com.romrom.item.dto;

import com.romrom.common.constant.TradeReviewRating;
import com.romrom.common.constant.TradeReviewTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 받은 거래 후기 통계 (#858): 블라인드 후기 제외 전체 기준
 */
@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeReviewStats {

  @Schema(description = "받은 후기 수 (블라인드 제외)")
  private Long totalReviewCount;

  @Schema(description = "평균 평점 (BAD 1, GOOD 2, GREAT 3, 후기 없음 > null, 소수 둘째 자리 반올림)")
  private Double averageRatingScore;

  @Schema(description = "평점별 후기 수 (모든 평점 포함, 없으면 0)")
  private Map<TradeReviewRating, Long> ratingCounts;

  @Schema(description = "세부 태그별 선택 수 (모든 태그 포함, 없으면 0)")
  private Map<TradeReviewTag, Long> tagCounts;
}
