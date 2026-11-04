package com.romrom.member.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.romrom.common.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 닉네임 규칙: 길이 2~20, 허용 문자, 예약어 (#862)
 */
class NicknameRuleTest {

  @Test
  @DisplayName("허용 문자 2~20자 > 통과 (자동 생성 닉네임 형식 포함)")
  void allowsValidNicknames() {
    assertTrue(NicknameRule.findViolation("가냘픈나무1234").isEmpty());
    assertTrue(NicknameRule.findViolation("ab").isEmpty());
    assertTrue(NicknameRule.findViolation("trade_lover_99").isEmpty());
    assertTrue(NicknameRule.findViolation("가".repeat(NicknameRule.MAXIMUM_LENGTH)).isEmpty());
  }

  @Test
  @DisplayName("1자·21자·null > 길이 위반")
  void rejectsInvalidLength() {
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_LENGTH), NicknameRule.findViolation("가"));
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_LENGTH), NicknameRule.findViolation("가".repeat(21)));
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_LENGTH), NicknameRule.findViolation(null));
  }

  @Test
  @DisplayName("공백·특수문자·자모 > 허용 문자 위반")
  void rejectsDisallowedCharacters() {
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_CHARACTER), NicknameRule.findViolation("롬 롬"));
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_CHARACTER), NicknameRule.findViolation(" 롬롬유저"));
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_CHARACTER), NicknameRule.findViolation("롬롬!"));
    assertEquals(Optional.of(ErrorCode.INVALID_NICKNAME_CHARACTER), NicknameRule.findViolation("ㄱㄴㄷ"));
  }

  @Test
  @DisplayName("예약어 포함 (대소문자 무시) > 예약어 위반")
  void rejectsReservedWords() {
    assertEquals(Optional.of(ErrorCode.RESERVED_NICKNAME), NicknameRule.findViolation("운영자"));
    assertEquals(Optional.of(ErrorCode.RESERVED_NICKNAME), NicknameRule.findViolation("진짜관리자1"));
    assertEquals(Optional.of(ErrorCode.RESERVED_NICKNAME), NicknameRule.findViolation("ADMIN_kim"));
    assertEquals(Optional.of(ErrorCode.RESERVED_NICKNAME), NicknameRule.findViolation("RomRom_official"));
  }
}
