package com.romrom.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로그 이메일 마스킹 검증 (#825)
 */
class LogMaskUtilTest {

  @Test
  @DisplayName("로컬 파트 앞 2자 + *** + 도메인")
  void 일반_이메일은_앞_2자만_남긴다() {
    assertEquals("ch***@gmail.com", LogMaskUtil.maskEmail("chancamera123@gmail.com"));
  }

  @Test
  @DisplayName("로컬 파트 2자 이하는 1자만 남겨 원문 노출 방지")
  void 짧은_로컬_파트는_1자만_남긴다() {
    assertEquals("a***@test.com", LogMaskUtil.maskEmail("ab@test.com"));
    assertEquals("a***@test.com", LogMaskUtil.maskEmail("a@test.com"));
  }

  @Test
  @DisplayName("null·빈 값은 그대로, @ 없는 값도 앞 2자만")
  void 이메일이_아닌_값도_안전하게_처리한다() {
    assertNull(LogMaskUtil.maskEmail(null));
    assertEquals("", LogMaskUtil.maskEmail(""));
    assertEquals("ji***", LogMaskUtil.maskEmail("jimhayn"));
  }

  @Test
  @DisplayName("문장 속 이메일 전부 마스킹, 이미 마스킹된 값과 이메일 없는 문장은 그대로")
  void 문장_속_이메일을_마스킹한다() {
    assertEquals("{\"email\" : \"ch***@gmail.com\", \"other\" : \"ji***@naver.com\"}",
        LogMaskUtil.maskEmailsInText("{\"email\" : \"chancamera123@gmail.com\", \"other\" : \"jimhayn@naver.com\"}"));
    assertEquals("회원: ch***@gmail.com", LogMaskUtil.maskEmailsInText("회원: ch***@gmail.com"));
    assertEquals("이메일 없음", LogMaskUtil.maskEmailsInText("이메일 없음"));
    assertNull(LogMaskUtil.maskEmailsInText(null));
  }
}
