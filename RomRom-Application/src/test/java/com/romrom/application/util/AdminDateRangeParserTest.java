package com.romrom.application.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 기간 필터 파싱 검증 (#814): 시작 00:00, 종료 23:59:59.999999999, 미입력 null, 형식 오류 400
 */
class AdminDateRangeParserTest {

  @Test
  @DisplayName("시작일은 00:00, 종료일은 그날 마지막 시각 (종료일 포함)")
  void 시작일과_종료일_경계를_변환한다() {
    assertEquals(LocalDate.of(2026, 10, 29).atStartOfDay(), AdminDateRangeParser.parseStartOfDay("2026-10-29"));
    assertEquals(LocalDate.of(2026, 10, 29).atTime(LocalTime.MAX), AdminDateRangeParser.parseEndOfDay(" 2026-10-29 "));
  }

  @Test
  @DisplayName("미입력은 null (전체 기간)")
  void 미입력은_전체_기간이다() {
    assertNull(AdminDateRangeParser.parseStartOfDay(null));
    assertNull(AdminDateRangeParser.parseEndOfDay("  "));
  }

  @Test
  @DisplayName("형식 오류는 INVALID_REQUEST (전체 조회로 바뀌지 않음)")
  void 형식_오류는_400이다() {
    CustomException parseException = assertThrows(CustomException.class,
        () -> AdminDateRangeParser.parseStartOfDay("2026/10/29"));
    assertEquals(ErrorCode.INVALID_REQUEST, parseException.getErrorCode());
  }
}
