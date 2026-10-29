package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

public interface AdminLogControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "관리자 로그 관리 화면용 로그 조회/검색 API 추가 (파일 직접 읽기, 레벨/키워드 필터)"),
    })
    @Operation(
        summary = "관리자 로그 조회/검색",
        description = """
        ## 인증: **ROLE_ADMIN**
        ## 요청 (multipart/form-data)
        - **`logLineCount`** (Integer, 선택, 기본 200, 최대 2000)
        - **`logLevelFilter`** (String, 선택): ERROR/WARN/INFO/DEBUG, '전체'/미입력=전체
        - **`logKeyword`** (String, 선택): 키워드 검색
        ## 반환 (AdminResponse.logLines): 시간순(오래된→최신) 로그 라인
        """
    )
    ResponseEntity<AdminResponse> queryLogs(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "에러 집계에 정렬 기준(logErrorSortBy: count 많은순/recent 최근순) 파라미터 추가"),
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "관리자 로그 에러 집계 API 추가 (예외 클래스별 발생횟수/마지막발생/대표메시지)"),
    })
    @Operation(
        summary = "관리자 로그 에러 집계",
        description = """
        ## 인증: **ROLE_ADMIN**
        ## 요청 (multipart/form-data)
        - **`logErrorWithinMinutes`** (Integer, 선택, 기본 60): 집계 기간(분)
        - **`logErrorSortBy`** (String, 선택, 기본 count): 정렬 기준 — `count`(발생횟수 많은순) / `recent`(마지막 발생 최근순)
        ## 반환 (AdminResponse.logErrorSummaries): 예외 클래스별 집계, 정렬 기준에 따라 정렬
        """
    )
    ResponseEntity<AdminResponse> aggregateLogErrors(AdminRequest request);

    @Operation(summary = "관리자 로그 파일 목록 + 용량/디스크 상태", description = "## 인증: **ROLE_ADMIN**\n현재 .log + 과거 .gz 목록, 총 용량, 디스크 여유공간 반환")
    ResponseEntity<AdminResponse> listLogFiles(AdminRequest request);

    @Operation(summary = "관리자 .gz 로그 조회/검색", description = "## 인증: **ROLE_ADMIN**\n과거 .gz 파일을 서버에서 압축 해제 후 레벨/키워드 필터 적용. logFileName 필수.")
    ResponseEntity<AdminResponse> queryGzLog(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "지원하지 않는 range는 500 대신 400(INVALID_REQUEST)"),
    })
    @Operation(summary = "관리자 로그 시간범위 다운로드", description = "## 인증: **ROLE_ADMIN**\n현재 romrom.log에서 최근 range(5m/1h/6h/24h) 라인을 잘라 다운로드\n\n## 에러코드\n- INVALID_REQUEST (400): 지원하지 않는 range")
    ResponseEntity<byte[]> downloadByTimeRange(String range);

    @Operation(summary = "관리자 로그 파일 통째 다운로드", description = "## 인증: **ROLE_ADMIN**\nfileName(.log 또는 .gz)을 화이트리스트 검증 후 스트리밍 다운로드")
    ResponseEntity<Resource> downloadLogFile(String fileName);
}
