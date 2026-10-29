package com.romrom.application.util;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 기간 필터 파싱: yyyy-MM-dd > 시작 00:00 / 종료 23:59:59.999999999 (종료일 포함)
 * 미입력은 null(전체 기간), 형식 오류는 INVALID_REQUEST (기간 오류가 전체 조회로 바뀌는 것 방지)
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminDateRangeParser {

  private static final DateTimeFormatter ADMIN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public static LocalDateTime parseStartOfDay(String dateString) {
    LocalDate localDate = parseLocalDate(dateString);
    return localDate == null ? null : localDate.atStartOfDay();
  }

  public static LocalDateTime parseEndOfDay(String dateString) {
    LocalDate localDate = parseLocalDate(dateString);
    return localDate == null ? null : localDate.atTime(LocalTime.MAX);
  }

  private static LocalDate parseLocalDate(String dateString) {
    if (dateString == null || dateString.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(dateString.trim(), ADMIN_DATE_FORMAT);
    } catch (Exception dateParseException) {
      log.warn("[admin date] rejected 400 : invalid date format");
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
  }
}
