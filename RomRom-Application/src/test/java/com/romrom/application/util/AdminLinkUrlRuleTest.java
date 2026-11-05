package com.romrom.application.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 입력 링크 규칙 (#873, 홈 팝업·공지 공통): https:// · romrom:// 만 허용
 */
class AdminLinkUrlRuleTest {

  @Test
  @DisplayName("https:// · romrom:// 시작 > 허용 (대소문자 무시)")
  void 허용_형식() {
    assertTrue(AdminLinkUrlRule.isAllowedLinkUrl("https://romrom.app/event"));
    assertTrue(AdminLinkUrlRule.isAllowedLinkUrl("romrom://item/detail?itemId=1"));
    assertTrue(AdminLinkUrlRule.isAllowedLinkUrl("HTTPS://ROMROM.APP"));
  }

  @Test
  @DisplayName("http://·javascript:·형식 없음·null > 거부")
  void 거부_형식() {
    assertFalse(AdminLinkUrlRule.isAllowedLinkUrl("http://romrom.app"));
    assertFalse(AdminLinkUrlRule.isAllowedLinkUrl("javascript:alert(1)"));
    assertFalse(AdminLinkUrlRule.isAllowedLinkUrl("romrom.app"));
    assertFalse(AdminLinkUrlRule.isAllowedLinkUrl(null));
  }
}
