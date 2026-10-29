package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminReportControllerDocs {

    ResponseEntity<AdminResponse> getItemReports(AdminRequest request);

    ResponseEntity<AdminResponse> getMemberReports(AdminRequest request);

    ResponseEntity<AdminResponse> getItemReportDetail(AdminRequest request);

    ResponseEntity<AdminResponse> getMemberReportDetail(AdminRequest request);

    ResponseEntity<AdminResponse> updateReportStatus(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "SUSPEND_MEMBER 시 관리자 계정 정지 차단(403), 제재 이력에 처리 관리자 기록"),
        @ApiChangeLog(
            date = "2026.06.13",
            author = Author.SUHSAECHAN,
            issueNumber = 709,
            description = "신고 원스톱 처리 API 추가 — 한 트랜잭션에서 피신고자 정지/물품삭제/반려 + 신고 상태 자동 변경 + 제재이력 연결"
        )
    })
    @Operation(
        summary = "신고 원스톱 처리",
        description = """
        ## 신고 원스톱 처리 (#709)
        신고 상세 화면에서 후속 조치까지 한 번에 처리한다.

        ## 요청 (AdminRequest)
        - **`reportId`** (UUID, 필수): 신고 ID
        - **`reportType`** (ITEM / MEMBER, 필수): 신고 유형
        - **`resolveAction`** (SUSPEND_MEMBER / DELETE_ITEM / REJECT, 필수): 처리 액션
        - **`suspendReason`, `suspendedUntil`** (선택): SUSPEND_MEMBER 시 정지 사유/기간
        - **`itemAdminDeleteDetail`** (선택): DELETE_ITEM 시 삭제 상세 사유

        ## 동작
        - SUSPEND_MEMBER: 피신고자 정지 + SanctionHistory에 reportId 연결 + 신고 COMPLETED
        - DELETE_ITEM: 신고 대상 물품 삭제 + 신고 COMPLETED (물품 신고만 가능)
        - REJECT: 액션 없이 신고 REJECTED

        ## 에러코드
        - INVALID_REQUEST (400): 필수값 누락, 회원 신고에 DELETE_ITEM 지정
        - REPORT_NOT_FOUND (404): 신고 없음
        - MEMBER_NOT_FOUND (404): 피신고자 없음
        - ITEM_NOT_FOUND (404): 신고 대상 물품 없음
        - ADMIN_TARGET_FORBIDDEN (403): 피신고자가 관리자 계정
        """
    )
    ResponseEntity<AdminResponse> resolveReport(AdminRequest request, CustomUserDetails principal);

    ResponseEntity<AdminResponse> getReportStats(AdminRequest request);
}
