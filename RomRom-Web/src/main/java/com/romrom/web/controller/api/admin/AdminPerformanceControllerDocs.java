package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminPerformanceControllerDocs {

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.03", author = Author.SUHSAECHAN, issueNumber = 844, description = "서버 성능 지표 조회 API 추가"),
    })
    @Operation(
        summary = "서버 성능 지표 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - 없음

        ## 반환값 (AdminResponse)
        - **`performanceSnapshot`**: 서버 기동 후 누적 지표
          - `uptimeSeconds`, `heapUsedBytes`, `heapMaxBytes`, `liveThreadCount`
          - `gcPauseCount`, `gcPauseTotalMilliseconds`
          - `databaseConnectionActiveCount`, `databaseConnectionIdleCount`, `databaseConnectionPendingCount`, `databaseConnectionMaxCount`, `databaseConnectionTimeoutCount`
          - `slowestRequests`: 평균 응답 시간 순 상위 15개 (3회 이상 호출된 API)
          - `busiestRequests`: 호출 수 순 상위 15개
          - 각 항목: `method`, `uri`, `requestCount`, `serverErrorCount`, `totalMilliseconds`, `averageMilliseconds`, `recentMaximumMilliseconds` (최근 몇 분 안의 최대값)
        """
    )
    ResponseEntity<AdminResponse> getPerformanceSnapshot(AdminRequest request);
}
