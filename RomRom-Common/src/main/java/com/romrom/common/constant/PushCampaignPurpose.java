package com.romrom.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 푸시 캠페인 목적 (#874): 정보성 > 콘텐츠 알림 동의자, 광고성 > 마케팅 수신 동의자·(광고) 표시·야간 금지
 */
@AllArgsConstructor
@Getter
public enum PushCampaignPurpose {
  INFORMATION("정보성"),
  ADVERTISEMENT("광고성");

  private final String description;
}
