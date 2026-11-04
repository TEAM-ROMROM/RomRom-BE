package com.romrom.notification.service;

import com.romrom.member.entity.Member;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 방해금지 시간 판정: 회원 설정(사용 여부·시작·종료, KST) <-> 현재 시각 (#861)
 * 시작 < 종료 > 같은 날 구간, 시작 > 종료 > 자정 넘김 구간, 시작 = 종료 > 구간 없음
 */
public final class DoNotDisturbTimeRule {

  // 방해금지 시각 기준 시간대
  public static final ZoneId DO_NOT_DISTURB_ZONE = ZoneId.of("Asia/Seoul");

  private DoNotDisturbTimeRule() {
  }

  /**
   * 현재 KST 시각 기준 방해금지 중 여부
   */
  public static boolean isDoNotDisturbNow(Member member) {
    return isDoNotDisturbAt(member, LocalTime.now(DO_NOT_DISTURB_ZONE));
  }

  /**
   * 주어진 KST 시각 기준 방해금지 중 여부 (꺼짐·시각 누락 > false, 시작 포함·종료 제외)
   */
  public static boolean isDoNotDisturbAt(Member member, LocalTime kstTime) {
    if (member == null || !Boolean.TRUE.equals(member.getIsDoNotDisturbEnabled())) {
      return false;
    }
    LocalTime startTime = member.getDoNotDisturbStartTime();
    LocalTime endTime = member.getDoNotDisturbEndTime();
    if (startTime == null || endTime == null || startTime.equals(endTime)) {
      return false;
    }
    boolean isOvernightWindow = startTime.isAfter(endTime);
    if (isOvernightWindow) {
      return !kstTime.isBefore(startTime) || kstTime.isBefore(endTime);
    }
    return !kstTime.isBefore(startTime) && kstTime.isBefore(endTime);
  }
}
