package com.romrom.member.util;

import com.romrom.common.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 닉네임 규칙 단일 원본: 길이(2~20자, UTF-16 기준), 허용 문자(한글·영문·숫자·밑줄), 예약어 포함 금지
 * 앱 lib/utils/nickname_rules.dart 와 같은 규칙 (바꾸면 함께 변경)
 */
public final class NicknameRule {

  public static final int MINIMUM_LENGTH = 2;
  public static final int MAXIMUM_LENGTH = 20;

  // 완성형 한글·영문·숫자·밑줄만 허용 (공백·자모·특수문자 불가)
  private static final Pattern ALLOWED_CHARACTER_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_]+$");

  // 운영 주체 사칭 방지 예약어 (대소문자 무시, 부분 포함도 금지)
  private static final List<String> RESERVED_WORDS = List.of("운영자", "관리자", "admin", "romrom");

  private NicknameRule() {
  }

  /**
   * 닉네임 규칙 위반 사유 (위반 없음 > empty, 검사 순서: 길이 > 허용 문자 > 예약어)
   */
  public static Optional<ErrorCode> findViolation(String nickname) {
    if (nickname == null || nickname.length() < MINIMUM_LENGTH || nickname.length() > MAXIMUM_LENGTH) {
      return Optional.of(ErrorCode.INVALID_NICKNAME_LENGTH);
    }
    if (!ALLOWED_CHARACTER_PATTERN.matcher(nickname).matches()) {
      return Optional.of(ErrorCode.INVALID_NICKNAME_CHARACTER);
    }
    if (containsReservedWord(nickname)) {
      return Optional.of(ErrorCode.RESERVED_NICKNAME);
    }
    return Optional.empty();
  }

  private static boolean containsReservedWord(String nickname) {
    String lowerCaseNickname = nickname.toLowerCase(Locale.ROOT);
    return RESERVED_WORDS.stream().anyMatch(lowerCaseNickname::contains);
  }
}
