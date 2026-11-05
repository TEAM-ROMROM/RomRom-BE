package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminAuditLogRequest;
import com.romrom.application.dto.AdminAuditLogResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminAuditLogControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 874, description = "관리자 작업 기록 목록 API 추가"),
  })
  @Operation(summary = "관리자 작업 기록 목록", description = """
      ## 인증: **관리자**

      ## 요청 파라미터 (AdminAuditLogRequest, multipart/form-data, 입력한 조건만 적용)
      - **`startDate`**, **`endDate`** (yyyy-MM-dd, 양끝 포함)
      - **`adminKeyword`**: 관리자 회원 ID(UUID, 정확히 일치) 또는 이메일 일부
      - **`adminAuditAction`**: 작업 종류 (예: `ITEM_DELETE`, `MEMBER_SUSPEND`, `CSV_EXPORT_MEMBERS`)
      - **`adminAuditTargetType`**: 대상 종류 (예: `ITEM`, `MEMBER`, `REPORT`)
      - **`targetId`**: 대상 ID (정확히 일치)
      - **`pageNumber`** (기본 0), **`pageSize`** (기본 20, 최대 100)

      ## 반환값 (AdminAuditLogResponse)
      - **`adminAuditLogs`**: 최근순 목록 — `adminMemberId`, `adminEmail`, `action`, `targetType`, `targetId`, `summary`, `requestSummary`(JSON 문자열), `clientIp`, `createdDate`
      - **`totalPages`**, **`totalElements`**, **`currentPage`**

      ## 기록 규칙
      - 관리자 변경 API(`@AdminAudit`)가 **정상 처리된 경우에만** 1행 기록 (실패 요청은 서버 로그)
      - `requestSummary`: 요청 파라미터 요약 — 이름에 password·secret·token 이 들어간 값은 `***`(ID 제외), 긴 값 200자 잘림, 파일은 이름·크기만, 이메일 마스킹

      ## 에러코드
      - **`INVALID_REQUEST`**: 날짜 형식 오류
      """)
  ResponseEntity<AdminAuditLogResponse> getAuditLogs(AdminAuditLogRequest request);
}
