package com.romrom.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그용 개인정보 마스킹: 이메일 로컬 파트 앞 2자만 남김 (#825)
 * 회원 아이디(=이메일)를 로그에 남길 때는 반드시 이 메서드를 거침 (규칙: no-raw-email-log)
 */
public final class LogMaskUtil {

  // 로컬 파트에서 남길 앞글자 수
  private static final int VISIBLE_LOCAL_PART_LENGTH = 2;
  private static final String MASK = "***";
  // 문장 속 이메일 (로컬 파트 1자 이상 + 도메인). 마스킹된 "ab***@" 는 '*'가 로컬 파트 문자가 아니라 재마스킹 안 됨
  private static final Pattern EMAIL_IN_TEXT_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private LogMaskUtil() {
  }

  /**
   * 문장 속 이메일 전부 마스킹 (로그 출력 단계 안전망: 응답 본문 JSON 등)
   */
  public static String maskEmailsInText(String text) {
    if (text == null || text.indexOf('@') < 0) {
      return text;
    }
    Matcher emailMatcher = EMAIL_IN_TEXT_PATTERN.matcher(text);
    StringBuilder maskedText = new StringBuilder(text.length());
    while (emailMatcher.find()) {
      emailMatcher.appendReplacement(maskedText, Matcher.quoteReplacement(maskEmail(emailMatcher.group())));
    }
    emailMatcher.appendTail(maskedText);
    return maskedText.toString();
  }

  /**
   * 이메일 > 마스킹 문자열: abcdef@gmail.com > ab***@gmail.com, 1~2자 로컬 파트 > a***@, null·빈 값 > 그대로
   * @ 없는 값(이메일 아님)도 앞 2자만 남김
   */
  public static String maskEmail(String email) {
    if (email == null || email.isBlank()) {
      return email;
    }
    int atIndex = email.indexOf('@');
    String localPart = atIndex >= 0 ? email.substring(0, atIndex) : email;
    String domainPart = atIndex >= 0 ? email.substring(atIndex) : "";
    int visibleLength = localPart.length() > VISIBLE_LOCAL_PART_LENGTH ? VISIBLE_LOCAL_PART_LENGTH : 1;
    return localPart.substring(0, Math.min(visibleLength, localPart.length())) + MASK + domainPart;
  }
}
