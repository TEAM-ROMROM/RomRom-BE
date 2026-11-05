package com.romrom.notification.push;

import com.romrom.common.constant.DeviceType;
import java.util.UUID;

/**
 * 발송 대상 토큰 1개: 토큰 값, 소유 회원, 기기 종류
 */
public record FcmPushTarget(String token, UUID memberId, DeviceType deviceType) {

  @Override
  public String toString() {
    // 토큰 원문 로그 방지
    return "FcmPushTarget[memberId=" + memberId + ", deviceType=" + deviceType + "]";
  }
}
