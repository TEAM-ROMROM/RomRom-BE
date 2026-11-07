package com.romrom.common.constant;

import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 서비스 기준 시간대 단일 원본 (KST): 스케줄러 cron, 일·시간 경계, Mongo 집계 timezone
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceTimeZone {

  // @Scheduled(zone)·Mongo timezone 인자용 (컴파일 상수 문자열)
  public static final String KST_ZONE_ID_NAME = "Asia/Seoul";
  public static final ZoneId KST_ZONE_ID = ZoneId.of(KST_ZONE_ID_NAME);
}
