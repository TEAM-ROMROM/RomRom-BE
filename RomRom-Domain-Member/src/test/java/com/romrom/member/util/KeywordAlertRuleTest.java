package com.romrom.member.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 키워드 알림 규칙 (#873): 정규화, 길이 2~20, 물품 문구 포함 판정
 */
class KeywordAlertRuleTest {

  @Test
  @DisplayName("정규화: 앞뒤 공백 제거 > 연속 공백·줄바꿈 1칸 > 소문자")
  void normalize_공백정리_소문자() {
    assertEquals("닌텐도 switch", KeywordAlertRule.normalize("  닌텐도   Switch \n"));
    assertEquals("a b", KeywordAlertRule.normalize("A\t\tB"));
    assertEquals("", KeywordAlertRule.normalize(null));
    assertEquals("", KeywordAlertRule.normalize("   "));
  }

  @Test
  @DisplayName("표시용 키워드: 공백만 정리하고 대소문자 유지")
  void toDisplayKeyword_대소문자유지() {
    assertEquals("닌텐도 Switch", KeywordAlertRule.toDisplayKeyword("  닌텐도   Switch "));
    assertEquals("", KeywordAlertRule.toDisplayKeyword(null));
  }

  @Test
  @DisplayName("길이: 정규화 후 2~20자만 허용 (공백만 입력 > 0자)")
  void isAllowedLength_경계() {
    assertTrue(KeywordAlertRule.isAllowedLength(KeywordAlertRule.normalize("ab")));
    assertTrue(KeywordAlertRule.isAllowedLength(KeywordAlertRule.normalize("가".repeat(KeywordAlertRule.MAXIMUM_LENGTH))));
    assertFalse(KeywordAlertRule.isAllowedLength(KeywordAlertRule.normalize(" 가 ")));
    assertFalse(KeywordAlertRule.isAllowedLength(KeywordAlertRule.normalize("가".repeat(KeywordAlertRule.MAXIMUM_LENGTH + 1))));
    assertFalse(KeywordAlertRule.isAllowedLength(KeywordAlertRule.normalize("    ")));
    assertFalse(KeywordAlertRule.isAllowedLength(null));
  }

  @Test
  @DisplayName("같은 정규화 값 > 중복 판정 기준 동일")
  void normalize_중복기준() {
    assertEquals(KeywordAlertRule.normalize("Switch  OLED"), KeywordAlertRule.normalize(" switch oled "));
  }

  @Test
  @DisplayName("매칭: 이름 + 설명 정규화 문구에 키워드 포함 (대소문자·연속 공백 무시)")
  void isKeywordContained_이름설명() {
    String matchingText = KeywordAlertRule.toMatchingText("닌텐도  SWITCH 팝니다", "박스 포함\n상태 좋아요");

    assertTrue(KeywordAlertRule.isKeywordContained(matchingText, KeywordAlertRule.normalize("닌텐도 switch")));
    assertTrue(KeywordAlertRule.isKeywordContained(matchingText, KeywordAlertRule.normalize("상태 좋아요")));
    assertTrue(KeywordAlertRule.isKeywordContained(matchingText, KeywordAlertRule.normalize("팝니다 박스")));
    assertFalse(KeywordAlertRule.isKeywordContained(matchingText, KeywordAlertRule.normalize("플레이스테이션")));
    assertFalse(KeywordAlertRule.isKeywordContained(matchingText, ""));
  }

  @Test
  @DisplayName("매칭 문구: 설명 null 이어도 이름만으로 판정")
  void toMatchingText_설명없음() {
    String matchingText = KeywordAlertRule.toMatchingText("아이패드 미니", null);

    assertTrue(KeywordAlertRule.isKeywordContained(matchingText, "아이패드"));
  }
}
