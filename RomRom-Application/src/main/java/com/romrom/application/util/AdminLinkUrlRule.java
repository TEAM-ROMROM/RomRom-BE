package com.romrom.application.util;

import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 관리자 입력 링크 규칙 (홈 팝업·공지 공통): 허용 형식 2개 > 외부 브라우저 https://, 앱 딥링크 romrom://
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminLinkUrlRule {

  private static final List<String> ALLOWED_LINK_URL_PREFIXES = List.of("https://", "romrom://");

  /**
   * 허용 형식 여부 (앞뒤 공백 제거된 값, 대소문자 무시)
   */
  public static boolean isAllowedLinkUrl(String trimmedLinkUrl) {
    if (trimmedLinkUrl == null) {
      return false;
    }
    String lowerCaseLinkUrl = trimmedLinkUrl.toLowerCase(Locale.ROOT);
    return ALLOWED_LINK_URL_PREFIXES.stream().anyMatch(lowerCaseLinkUrl::startsWith);
  }
}
