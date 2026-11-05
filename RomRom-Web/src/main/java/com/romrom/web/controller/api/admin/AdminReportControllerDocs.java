package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminChatMessageReportResponse;
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

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "채팅 메시지 신고 목록 조회 추가"),
    })
    @Operation(
        summary = "채팅 메시지 신고 목록",
        description = """
        ## 요청 (AdminRequest)
        - **`pageNumber`**, **`pageSize`**: 페이지 (0부터)
        - **`reportStatus`** (선택): PENDING / PROCESSING / COMPLETED / REJECTED

        ## 응답 (AdminChatMessageReportResponse)
        - **`chatMessageReports`**: 접수 최신순 (reporter, targetMember, chatRoomId, chatMessageId, messageContentSnapshot, reasons, extraComment, status, createdDate)
        - **`totalPages`**, **`totalElements`**, **`currentPage`**
        """
    )
    ResponseEntity<AdminChatMessageReportResponse> getChatMessageReports(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "채팅 메시지 신고 상세 조회 추가"),
    })
    @Operation(
        summary = "채팅 메시지 신고 상세",
        description = """
        ## 요청 (AdminRequest)
        - **`reportId`** (UUID, 필수): 채팅 메시지 신고 ID

        ## 응답 (AdminChatMessageReportResponse)
        - **`chatMessageReport`**: 신고 상세 (메시지 원문 스냅샷 포함)
        - **`reportResolveDetail`**: reportType=CHAT_MESSAGE, 대상 회원 상태, 누적 신고(물품+회원+채팅)

        ## 에러코드
        - INVALID_REQUEST (400): reportId 누락
        - REPORT_NOT_FOUND (404): 신고 없음
        """
    )
    ResponseEntity<AdminChatMessageReportResponse> getChatMessageReportDetail(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "reportType=CHAT_MESSAGE 상태 변경 지원"),
    })
    @Operation(
        summary = "신고 상태 변경",
        description = """
        ## 요청 (AdminRequest)
        - **`reportId`** (UUID, 필수), **`reportType`** (ITEM / MEMBER / CHAT_MESSAGE, 필수), **`newReportStatus`** (필수)

        ## 에러코드
        - INVALID_REQUEST (400): 신고 유형 누락
        - REPORT_NOT_FOUND (404): 신고 없음
        """
    )
    ResponseEntity<AdminResponse> updateReportStatus(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "reportType=CHAT_MESSAGE 처리 지원 (SUSPEND_MEMBER·REJECT)"),
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
        - **`reportType`** (ITEM / MEMBER / CHAT_MESSAGE, 필수): 신고 유형
        - **`resolveAction`** (SUSPEND_MEMBER / DELETE_ITEM / REJECT, 필수): 처리 액션
        - **`suspendReason`, `suspendedUntil`** (선택): SUSPEND_MEMBER 시 정지 사유/기간
        - **`itemAdminDeleteDetail`** (선택): DELETE_ITEM 시 삭제 상세 사유

        ## 동작
        - SUSPEND_MEMBER: 피신고자 정지 + SanctionHistory에 reportId 연결 + 신고 COMPLETED
        - DELETE_ITEM: 신고 대상 물품 삭제 + 신고 COMPLETED (물품 신고만 가능)
        - CHAT_MESSAGE: 피신고자 = 메시지 보낸 회원(targetMember), SUSPEND_MEMBER·REJECT만 가능
        - REJECT: 액션 없이 신고 REJECTED

        ## 에러코드
        - INVALID_REQUEST (400): 필수값 누락, 회원·채팅 메시지 신고에 DELETE_ITEM 지정
        - REPORT_NOT_FOUND (404): 신고 없음
        - MEMBER_NOT_FOUND (404): 피신고자 없음
        - ITEM_NOT_FOUND (404): 신고 대상 물품 없음
        - ADMIN_TARGET_FORBIDDEN (403): 피신고자가 관리자 계정
        """
    )
    ResponseEntity<AdminResponse> resolveReport(AdminRequest request, CustomUserDetails principal);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.11.05", author = Author.SUHSAECHAN, issueNumber = 873, description = "reportStats에 chatMessage 상태별 건수 추가"),
    })
    @Operation(
        summary = "신고 상태별 통계",
        description = """
        ## 응답 (AdminResponse.reportStats)
        - 키 3개: **`item`**, **`member`**, **`chatMessage`** > 각 상태(PENDING·PROCESSING·COMPLETED·REJECTED)별 건수
        """
    )
    ResponseEntity<AdminResponse> getReportStats(AdminRequest request);
}
