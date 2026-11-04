package com.romrom.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 롬업 사전 자격 (#858): 광고 시청 전 확인용, 활성화 API 한도 판정과 같은 기준
 */
@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemPromotionEligibility {

  @Schema(description = "지금 롬업 가능 여부")
  private Boolean isEligible;

  @Schema(description = "불가 사유 errorCode (ITEM_NOT_PROMOTABLE / ITEM_PROMOTION_ALREADY_ACTIVE / ITEM_PROMOTION_DAILY_LIMIT_EXCEEDED, 가능 > null)")
  private String ineligibleReason;

  @Schema(description = "회원 하루(KST) 최대 롬업 횟수")
  private Long dailyPromotionLimit;

  @Schema(description = "오늘(KST) 남은 롬업 횟수")
  private Long remainingDailyPromotionCount;

  @Schema(description = "물품 재활성화 제한 종료 일시 (제한 없음 > null)")
  private LocalDateTime itemCooldownEndsAt;

  @Schema(description = "하루 횟수 초기화 일시 (다음 KST 0시)")
  private LocalDateTime dailyLimitResetsAt;

  @Schema(description = "다음 롬업 가능 일시 (지금 가능·교환 불가 물품 > null)")
  private LocalDateTime nextAvailableAt;
}
