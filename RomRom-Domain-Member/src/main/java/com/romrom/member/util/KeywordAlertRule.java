package com.romrom.member.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 키워드 알림 규칙 단일 원본 (#873): 정규화, 길이(2~20자, 정규화 후 UTF-16 기준), 회원당 최대 개수, 물품 문구 포함 판정
 * 앱 키워드 알림 화면 안내 문구와 같은 값 (바꾸면 함께 변경)
 */
public final class KeywordAlertRule {

  public static final int MINIMUM_LENGTH = 2;
  public static final int MAXIMUM_LENGTH = 20;
  // 회원당 등록 가능한 키워드 수
  public static final int MAXIMUM_KEYWORD_ALERT_COUNT = 10;

  // 공백·줄바꿈·탭 연속 구간
  private static final Pattern CONSECUTIVE_WHITESPACE_PATTERN = Pattern.compile("\\s+");
  private static final String SINGLE_SPACE = " ";

  private KeywordAlertRule() {
  }

  /**
   * 표시용 키워드: 앞뒤 공백 제거 > 연속 공백 1칸 (대소문자 유지, null > 빈 문자열)
   */
  public static String toDisplayKeyword(String keyword) {
    if (keyword == null) {
      return "";
    }
    return CONSECUTIVE_WHITESPACE_PATTERN.matcher(keyword.trim()).replaceAll(SINGLE_SPACE);
  }

  /**
   * 비교용 키워드: 표시용 정리 > 소문자 (중복 판정·매칭 기준)
   */
  public static String normalize(String keyword) {
    return toDisplayKeyword(keyword).toLowerCase(Locale.ROOT);
  }

  /**
   * 정규화 키워드 길이 허용 여부 (2~20자)
   */
  public static boolean isAllowedLength(String normalizedKeyword) {
    return normalizedKeyword != null
        && normalizedKeyword.length() >= MINIMUM_LENGTH
        && normalizedKeyword.length() <= MAXIMUM_LENGTH;
  }

  /**
   * 물품 이름 + 설명 > 매칭 대상 문구 (키워드와 같은 정규화, null 항목은 빈 문자열)
   */
  public static String toMatchingText(String itemName, String itemDescription) {
    String itemNameText = itemName == null ? "" : itemName;
    String itemDescriptionText = itemDescription == null ? "" : itemDescription;
    return normalize(itemNameText + SINGLE_SPACE + itemDescriptionText);
  }

  /**
   * 매칭 대상 문구에 정규화 키워드 포함 여부
   */
  public static boolean isKeywordContained(String matchingText, String normalizedKeyword) {
    if (matchingText == null || normalizedKeyword == null || normalizedKeyword.isEmpty()) {
      return false;
    }
    return matchingText.contains(normalizedKeyword);
  }
}
