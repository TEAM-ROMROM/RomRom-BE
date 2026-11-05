package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 푸시 캠페인 상태 (#874): 예약 > 발송 중 > 완료·실패, 예약 상태에서만 취소
 */
@AllArgsConstructor
@Getter
public enum PushCampaignStatus {
  SCHEDULED("예약"),
  SENDING("발송 중"),
  COMPLETED("완료"),
  FAILED("실패"),
  CANCELED("취소");

  private final String description;
}
