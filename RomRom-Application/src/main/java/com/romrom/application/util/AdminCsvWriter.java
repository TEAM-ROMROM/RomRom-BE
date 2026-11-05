package com.romrom.application.util;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 관리자 CSV 쓰기 (#874): UTF-8 BOM, RFC 4180 이스케이프, 수식 주입 방지, 묶음 단위 행 쓰기(최대 행 수)
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdminCsvWriter {

  // 엑셀 한글 깨짐 방지
  public static final char UTF8_BYTE_ORDER_MARK = '﻿';
  private static final String LINE_SEPARATOR = "\r\n";
  // 셀 시작이 이 문자면 수식으로 실행됨 (=, +, -, @, 탭, CR)
  private static final String FORMULA_LEADING_CHARACTERS = "=+-@\t\r";
  private static final String FORMULA_GUARD_PREFIX = "'";
  private static final DateTimeFormatter CSV_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * 셀 값 > CSV 필드 (null 빈 칸, 수식 시작 문자 앞 ', 쉼표·따옴표·줄바꿈 포함 시 따옴표 감싸기)
   */
  public static String toCsvField(String cellValue) {
    if (cellValue == null || cellValue.isEmpty()) {
      return "";
    }
    String guardedCellValue = FORMULA_LEADING_CHARACTERS.indexOf(cellValue.charAt(0)) >= 0
        ? FORMULA_GUARD_PREFIX + cellValue : cellValue;
    boolean isQuotingRequired = guardedCellValue.indexOf(',') >= 0 || guardedCellValue.indexOf('"') >= 0
        || guardedCellValue.indexOf('\n') >= 0 || guardedCellValue.indexOf('\r') >= 0;
    if (!isQuotingRequired) {
      return guardedCellValue;
    }
    return '"' + guardedCellValue.replace("\"", "\"\"") + '"';
  }

  /**
   * 한 행 쓰기 (필드 이스케이프 > 쉼표 연결 > CRLF)
   */
  public static void writeRow(Writer csvWriter, List<String> cellValues) throws IOException {
    for (int cellIndex = 0; cellIndex < cellValues.size(); cellIndex++) {
      if (cellIndex > 0) {
        csvWriter.write(',');
      }
      csvWriter.write(toCsvField(cellValues.get(cellIndex)));
    }
    csvWriter.write(LINE_SEPARATOR);
  }

  /**
   * BOM + 머리글 + 묶음 조회 반복 > 행 쓰기: 빈 묶음·묶음 크기 미만·최대 행 수 도달 시 종료, 쓴 행 수 반환
   * chunkReader: 묶음 번호(0부터) > 행 목록, afterChunkWritten: 묶음마다 1회 (영속성 컨텍스트 비우기 등)
   */
  public static <T> int writeAllRows(Writer csvWriter, List<String> headerCells, IntFunction<List<T>> chunkReader,
      Function<T, List<String>> rowConverter, int chunkSize, int maximumRowCount, Runnable afterChunkWritten)
      throws IOException {
    csvWriter.write(UTF8_BYTE_ORDER_MARK);
    writeRow(csvWriter, headerCells);
    int writtenRowCount = 0;
    for (int chunkNumber = 0; writtenRowCount < maximumRowCount; chunkNumber++) {
      List<T> chunkRows = chunkReader.apply(chunkNumber);
      if (chunkRows == null || chunkRows.isEmpty()) {
        break;
      }
      for (T chunkRow : chunkRows) {
        if (writtenRowCount >= maximumRowCount) {
          break;
        }
        writeRow(csvWriter, rowConverter.apply(chunkRow));
        writtenRowCount++;
      }
      csvWriter.flush();
      afterChunkWritten.run();
      if (chunkRows.size() < chunkSize) {
        break;
      }
    }
    csvWriter.flush();
    return writtenRowCount;
  }

  public static String toCsvDateTime(LocalDateTime dateTime) {
    return dateTime == null ? "" : dateTime.format(CSV_DATE_TIME_FORMAT);
  }

  public static String toCsvText(Object cellValue) {
    return cellValue == null ? "" : String.valueOf(cellValue);
  }
}
