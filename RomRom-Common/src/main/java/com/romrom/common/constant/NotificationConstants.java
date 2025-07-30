package com.romrom.common.constant;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class NotificationConstants {

  // FCM Token
  public static final long FCM_TOKEN_TTL = 30 * 24 * 60 * 60L; // FCM 토큰 30일 후 파기

  // Notification
  public static final String NOTIFICATION_ICON_PATH = "/romrom-logo.png";
}