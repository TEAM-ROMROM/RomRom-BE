package com.romrom.common.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class NotificationConstants {

  // FCM 토큰 정리 기준 (갱신 후 90일, 앱은 시작·토큰 갱신마다 재등록)
  public static final int FCM_TOKEN_STALE_DAYS = 90;
}
