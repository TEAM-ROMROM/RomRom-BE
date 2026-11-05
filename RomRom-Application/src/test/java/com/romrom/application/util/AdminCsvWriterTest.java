package com.romrom.application.util;

import com.romrom.application.service.AdminCsvExportService;
import com.romrom.common.util.LogMaskUtil;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 관리자 CSV 쓰기 단위 테스트 (#874): 이스케이프, 수식 주입 방지, BOM, 최대 행 수, 묶음 종료, 이메일 마스킹, 파일명
 */
class AdminCsvWriterTest {

  private static final int CHUNK_SIZE = 1_000;

  @Test
  @DisplayName("이스케이프: 쉼표·따옴표·줄바꿈은 따옴표 감싸기, 따옴표는 두 번, null 빈 칸")
  void escapesSpecialCharacters() {
    Assertions.assertEquals("plain", AdminCsvWriter.toCsvField("plain"));
    Assertions.assertEquals("\"a,b\"", AdminCsvWriter.toCsvField("a,b"));
    Assertions.assertEquals("\"say \"\"hi\"\"\"", AdminCsvWriter.toCsvField("say \"hi\""));
    Assertions.assertEquals("\"line1\nline2\"", AdminCsvWriter.toCsvField("line1\nline2"));
    Assertions.assertEquals("\"line1\r\nline2\"", AdminCsvWriter.toCsvField("line1\r\nline2"));
    Assertions.assertEquals("", AdminCsvWriter.toCsvField(null));
    Assertions.assertEquals("", AdminCsvWriter.toCsvField(""));
  }

  @Test
  @DisplayName("수식 주입 방지: = + - @ 탭으로 시작하면 앞에 '")
  void guardsFormulaInjection() {
    Assertions.assertEquals("'=SUM(A1:A2)", AdminCsvWriter.toCsvField("=SUM(A1:A2)"));
    Assertions.assertEquals("'+82", AdminCsvWriter.toCsvField("+82"));
    Assertions.assertEquals("'-1", AdminCsvWriter.toCsvField("-1"));
    Assertions.assertEquals("'@cmd", AdminCsvWriter.toCsvField("@cmd"));
    Assertions.assertEquals("'\tvalue", AdminCsvWriter.toCsvField("\tvalue"));
    // 수식 방지 + 쉼표 > 둘 다 적용
    Assertions.assertEquals("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"", AdminCsvWriter.toCsvField("=HYPERLINK(\"x\",\"y\")"));
    Assertions.assertEquals("a=b", AdminCsvWriter.toCsvField("a=b"));
  }

  @Test
  @DisplayName("BOM + 머리글 + 행, CRLF 줄바꿈")
  void writesBomHeaderAndRows() throws Exception {
    StringWriter csvOutput = new StringWriter();

    int writtenRowCount = AdminCsvWriter.writeAllRows(csvOutput, List.of("이름", "메모"),
        chunkNumber -> chunkNumber == 0 ? List.of("홍길동") : Collections.<String>emptyList(),
        name -> List.of(name, "메모, 포함"), CHUNK_SIZE, AdminCsvExportService.MAXIMUM_EXPORT_ROW_COUNT, () -> { });

    Assertions.assertEquals(1, writtenRowCount);
    Assertions.assertEquals("﻿이름,메모\r\n홍길동,\"메모, 포함\"\r\n", csvOutput.toString());
  }

  @Test
  @DisplayName("최대 10,000행: 묶음이 계속 와도 10,000행에서 멈춤, 묶음 10번 조회")
  void capsRowCount() throws Exception {
    StringWriter csvOutput = new StringWriter();
    AtomicInteger chunkReadCount = new AtomicInteger();
    AtomicInteger afterChunkCount = new AtomicInteger();
    List<Integer> fullChunk = IntStream.range(0, CHUNK_SIZE).boxed().collect(Collectors.toList());

    int writtenRowCount = AdminCsvWriter.writeAllRows(csvOutput, List.of("번호"),
        chunkNumber -> {
          chunkReadCount.incrementAndGet();
          return fullChunk;
        },
        rowNumber -> List.of(String.valueOf(rowNumber)), CHUNK_SIZE, AdminCsvExportService.MAXIMUM_EXPORT_ROW_COUNT,
        afterChunkCount::incrementAndGet);

    Assertions.assertEquals(10_000, writtenRowCount);
    Assertions.assertEquals(10, chunkReadCount.get());
    Assertions.assertEquals(10, afterChunkCount.get());
    long lineCount = csvOutput.toString().split("\r\n").length;
    Assertions.assertEquals(10_001, lineCount);
  }

  @Test
  @DisplayName("최대 행 수가 묶음 중간이면 그 행에서 멈춤")
  void capsRowCountInsideChunk() throws Exception {
    List<Integer> fullChunk = IntStream.range(0, CHUNK_SIZE).boxed().collect(Collectors.toList());

    int writtenRowCount = AdminCsvWriter.writeAllRows(new StringWriter(), List.of("번호"), chunkNumber -> fullChunk,
        rowNumber -> List.of(String.valueOf(rowNumber)), CHUNK_SIZE, 1_500, () -> { });

    Assertions.assertEquals(1_500, writtenRowCount);
  }

  @Test
  @DisplayName("묶음 크기보다 적게 오면 다음 묶음 조회 안 함")
  void stopsAfterShortChunk() throws Exception {
    List<Integer> chunkNumbers = new ArrayList<>();

    int writtenRowCount = AdminCsvWriter.writeAllRows(new StringWriter(), List.of("번호"),
        chunkNumber -> {
          chunkNumbers.add(chunkNumber);
          return chunkNumber == 0 ? IntStream.range(0, CHUNK_SIZE).boxed().toList() : List.of(1, 2, 3);
        },
        rowNumber -> List.of(String.valueOf(rowNumber)), CHUNK_SIZE, AdminCsvExportService.MAXIMUM_EXPORT_ROW_COUNT,
        () -> { });

    Assertions.assertEquals(CHUNK_SIZE + 3, writtenRowCount);
    Assertions.assertEquals(List.of(0, 1), chunkNumbers);
  }

  @Test
  @DisplayName("이메일 마스킹 (CSV 회원 이메일 열)")
  void masksEmail() {
    Assertions.assertEquals("ab***@gmail.com", LogMaskUtil.maskEmail("abcdef@gmail.com"));
    Assertions.assertEquals("a***@gmail.com", LogMaskUtil.maskEmail("ab@gmail.com"));
  }

  @Test
  @DisplayName("파일명: romrom_{대상}_yyyyMMdd_HHmm.csv")
  void buildsFileName() {
    Assertions.assertEquals("romrom_members_20261105_0930.csv",
        AdminCsvExportService.buildFileName("members", LocalDateTime.of(2026, 11, 5, 9, 30, 15)));
  }
}
