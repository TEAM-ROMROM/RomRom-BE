package com.romrom.notification.service;

import com.romrom.member.entity.Member;
import java.time.LocalTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 방해금지 시간 판정 단위 테스트 (#861): 꺼짐, 자정 넘김 구간, 같은 날 구간, 경계, 시작=종료
 */
class DoNotDisturbTimeRuleTest {

  private static Member createMember(boolean isEnabled, LocalTime startTime, LocalTime endTime) {
    return Member.builder()
        .isDoNotDisturbEnabled(isEnabled)
        .doNotDisturbStartTime(startTime)
        .doNotDisturbEndTime(endTime)
        .build();
  }

  @Test
  @DisplayName("기본값은 꺼짐 > 구간 안이어도 방해금지 아님")
  void isDoNotDisturbAt_기본값_꺼짐() {
    Member member = Member.builder().build();
    Assertions.assertFalse(member.getIsDoNotDisturbEnabled());
    Assertions.assertEquals(LocalTime.of(22, 0), member.getDoNotDisturbStartTime());
    Assertions.assertEquals(LocalTime.of(8, 0), member.getDoNotDisturbEndTime());
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(23, 0)));
  }

  @Test
  @DisplayName("자정 넘김 22:00~08:00: 23시·03시 포함, 12시 제외")
  void isDoNotDisturbAt_자정넘김() {
    Member member = createMember(true, LocalTime.of(22, 0), LocalTime.of(8, 0));
    Assertions.assertTrue(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(23, 0)));
    Assertions.assertTrue(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(3, 0)));
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(12, 0)));
  }

  @Test
  @DisplayName("경계: 시작 포함, 종료 제외")
  void isDoNotDisturbAt_경계() {
    Member member = createMember(true, LocalTime.of(22, 0), LocalTime.of(8, 0));
    Assertions.assertTrue(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(22, 0)));
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(8, 0)));
  }

  @Test
  @DisplayName("같은 날 13:00~15:00: 14시 포함, 16시 제외")
  void isDoNotDisturbAt_같은날() {
    Member member = createMember(true, LocalTime.of(13, 0), LocalTime.of(15, 0));
    Assertions.assertTrue(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(14, 0)));
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(16, 0)));
  }

  @Test
  @DisplayName("시작 = 종료면 구간 없음")
  void isDoNotDisturbAt_시작종료같음() {
    Member member = createMember(true, LocalTime.of(9, 0), LocalTime.of(9, 0));
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(member, LocalTime.of(9, 0)));
  }

  @Test
  @DisplayName("회원 없음 > 방해금지 아님")
  void isDoNotDisturbAt_회원없음() {
    Assertions.assertFalse(DoNotDisturbTimeRule.isDoNotDisturbAt(null, LocalTime.of(23, 0)));
  }
}
