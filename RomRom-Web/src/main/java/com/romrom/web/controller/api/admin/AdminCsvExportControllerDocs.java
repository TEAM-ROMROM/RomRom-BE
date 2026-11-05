package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;

public interface AdminCsvExportControllerDocs {

  String CSV_FORMAT_DESCRIPTION = """
      ## 응답 (파일 다운로드)
      - `text/csv; charset=UTF-8`, UTF-8 BOM 포함 (엑셀 한글 깨짐 방지)
      - 파일명: `romrom_{대상}_{yyyyMMdd_HHmm}.csv`
      - 최대 **10,000행** (초과분 제외), 서버에서 1,000행씩 나눠 조회
      - 쉼표·따옴표·줄바꿈 포함 값은 따옴표로 감쌈, `=` `+` `-` `@` 로 시작하는 값은 앞에 `'` 추가 (수식 실행 방지)
      - 개인정보 최소화: 이메일 마스킹(`ab***@gmail.com`), 전화번호·위치 없음
      - 요청 1회마다 관리자 작업 기록(admin_audit_log)에 남음

      ## 에러코드
      - **`INVALID_REQUEST`**: 날짜 형식 오류 (파일 응답 시작 전)
      """;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "회원 목록 CSV 내보내기 API 추가"),
  })
  @Operation(summary = "회원 목록 CSV 내보내기", description = """
      ## 인증: **관리자** (브라우저 다운로드용 GET, 쿠키 인증)

      ## 요청 파라미터 (쿼리, `/api/admin/members/list` 와 같은 필터)
      - **`searchKeyword`**, **`accountStatus`**, **`suspendType`**(permanent·temporary, accountStatus 와 함께), **`sortBy`**, **`sortDirection`**

      ## 열
      회원 ID, 닉네임, 이메일(마스킹), 가입 경로, 계정 상태, 마케팅 수신 동의, 받은 좋아요, 가입일, 마지막 활동, 정지 종료

      """ + CSV_FORMAT_DESCRIPTION)
  void exportMembersCsv(AdminRequest request, HttpServletResponse response) throws IOException;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "물품 목록 CSV 내보내기 API 추가"),
  })
  @Operation(summary = "물품 목록 CSV 내보내기", description = """
      ## 인증: **관리자** (브라우저 다운로드용 GET, 쿠키 인증)

      ## 요청 파라미터 (쿼리, `/api/admin/items/list` 와 같은 필터)
      - **`searchKeyword`**, **`itemCategory`**, **`itemCondition`**, **`itemStatus`**, **`minPrice`**, **`maxPrice`**, **`startDate`**·**`endDate`**(yyyy-MM-dd), **`sortBy`**, **`sortDirection`**

      ## 열
      물품 ID, 물품명, 카테고리, 상태, 거래 상태, 가격, 좋아요, 관리자 숨김, 등록자 ID, 등록자 닉네임, 등록일

      """ + CSV_FORMAT_DESCRIPTION)
  void exportItemsCsv(AdminRequest request, HttpServletResponse response) throws IOException;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "물품 신고 CSV 내보내기 API 추가"),
  })
  @Operation(summary = "물품 신고 CSV 내보내기", description = """
      ## 인증: **관리자** (브라우저 다운로드용 GET, 쿠키 인증)

      ## 요청 파라미터 (쿼리, `/api/admin/reports/item-list` 와 같은 필터)
      - **`reportStatus`** (선택, 미입력 전체)

      ## 열
      신고 ID, 처리 상태, 사유, 추가 설명, 물품 ID, 물품명, 물품 등록자 닉네임, 신고자 닉네임, 접수일 (접수 최신순)

      """ + CSV_FORMAT_DESCRIPTION)
  void exportItemReportsCsv(AdminRequest request, HttpServletResponse response) throws IOException;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "회원 신고 CSV 내보내기 API 추가"),
  })
  @Operation(summary = "회원 신고 CSV 내보내기", description = """
      ## 인증: **관리자** (브라우저 다운로드용 GET, 쿠키 인증)

      ## 요청 파라미터 (쿼리, `/api/admin/reports/member-list` 와 같은 필터)
      - **`reportStatus`** (선택, 미입력 전체)

      ## 열
      신고 ID, 처리 상태, 사유, 추가 설명, 신고 대상 ID·닉네임, 신고자 ID·닉네임, 접수일 (접수 최신순)

      """ + CSV_FORMAT_DESCRIPTION)
  void exportMemberReportsCsv(AdminRequest request, HttpServletResponse response) throws IOException;

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "채팅 신고 CSV 내보내기 API 추가"),
  })
  @Operation(summary = "채팅 신고 CSV 내보내기", description = """
      ## 인증: **관리자** (브라우저 다운로드용 GET, 쿠키 인증)

      ## 요청 파라미터 (쿼리, `/api/admin/reports/chat-message-list` 와 같은 필터)
      - **`reportStatus`** (선택, 미입력 전체)

      ## 열
      신고 ID, 처리 상태, 사유, 추가 설명, 채팅방 ID, 메시지 ID, 메시지 내용(신고 시점 스냅샷), 신고 대상 ID·닉네임, 신고자 닉네임, 접수일

      """ + CSV_FORMAT_DESCRIPTION)
  void exportChatMessageReportsCsv(AdminRequest request, HttpServletResponse response) throws IOException;
}
