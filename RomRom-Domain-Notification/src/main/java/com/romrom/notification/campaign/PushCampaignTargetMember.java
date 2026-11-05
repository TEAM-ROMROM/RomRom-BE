package com.romrom.notification.campaign;

import com.romrom.notification.service.DoNotDisturbTimeRule;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 대상 회원 1명의 발송 판정 값: 수신 동의, 방해금지 설정
 */
public record PushCampaignTargetMember(
    UUID memberId,
    boolean isPushConsented,
    Boolean isDoNotDisturbEnabled,
    LocalTime doNotDisturbStartTime,
    LocalTime doNotDisturbEndTime
) {

  /**
   * 푸시 가능 여부: 수신 동의 + 방해금지 시간 아님 (KST 시각)
   */
  public boolean isPushAllowedAt(LocalTime kstTime) {
    return isPushConsented
        && !DoNotDisturbTimeRule.isDoNotDisturbAt(isDoNotDisturbEnabled, doNotDisturbStartTime, doNotDisturbEndTime, kstTime);
  }
}
