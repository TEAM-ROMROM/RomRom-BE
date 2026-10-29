package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;

public interface AdminMemberControllerDocs {

    ResponseEntity<AdminResponse> getMembers(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 360 뷰 통합: 응답에 memberDetail360(12장 카드 통합 DTO) 추가. 기존 memberDetail은 호환을 위해 유지"
        )
    })
    @Operation(
        summary = "회원 상세 조회 (360 뷰)",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)

        ## 응답 (AdminResponse)
        - **`memberDetail`** (호환 유지): 기존 단순 상세 (member/items/memberReports/reportCount)
        - **`memberDetail360`**: 12장 카드 통합 DTO (계정/위치/물품/거래/채팅/신고받음/신고함/제재/로그인/좋아요/AI/알림 카운터 + 최근 활동)

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberDetail(AdminRequest request);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 보유 물품 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원 보유 물품 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)
        - **`pageNumber`** (int, 기본 0)
        - **`pageSize`** (int, 기본 20)
        - **`sortBy`** (String, 기본 "createdDate")
        - **`sortDirection`** (ASC/DESC, 기본 DESC)

        ## 응답 (AdminResponse)
        - **`memberItemsPage`**: Page<Item>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberItems(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 거래 이력 페이지네이션 조회 API 추가 (tradeSide=요청자/대상자/전체)"
        )
    })
    @Operation(
        summary = "회원 거래 이력 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)
        - **`tradeSide`** (String, 선택): "REQUESTER" / "OWNER" / "ALL" (기본 ALL)
        - 페이지네이션 파라미터 동일

        ## 응답 (AdminResponse)
        - **`memberTradesPage`**: Page<TradeRequestHistory>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberTrades(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 채팅방 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원 채팅방 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)
        - 페이지네이션 파라미터 동일

        ## 응답 (AdminResponse)
        - **`memberChatRoomsPage`**: Page<ChatRoom>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberChatRooms(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원이 신고당한 물품신고 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원이 받은 물품 신고 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberItemReportsReceivedPage`**: Page<ItemReport>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberItemReportsReceived(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원이 신고당한 회원신고 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원이 받은 회원 신고 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberMemberReportsReceivedPage`**: Page<MemberReport>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberMemberReportsReceived(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원이 접수한 물품신고 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원이 접수한 물품 신고 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberItemReportsFiledPage`**: Page<ItemReport>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberItemReportsFiled(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원이 접수한 회원신고 목록 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원이 접수한 회원 신고 목록",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberMemberReportsFiledPage`**: Page<MemberReport>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberMemberReportsFiled(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 알림 발송 이력 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원 알림 발송 이력",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberNotificationHistoryPage`**: Page<NotificationHistory>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberNotificationHistory(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.10.29", author = Author.SUHSAECHAN, issueNumber = 814, description = "loginResult 필터를 쿼리에서 적용 (페이지 내용·totalElements 모두 필터 기준)"),
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 로그인 이력 페이지네이션 조회 API 추가 (loginResult=SUCCESS/FAILED 필터)"
        )
    })
    @Operation(
        summary = "회원 로그인 이력",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)
        - **`loginResult`** (Enum, 선택): SUCCESS / FAILED. 미지정 시 전체
        - 페이지네이션 파라미터 동일

        ## 응답 (AdminResponse)
        - **`memberLoginHistoryPage`**: Page<LoginHistory>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberLoginHistory(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 좋아요 이력 페이지네이션 조회 API 추가"
        )
    })
    @Operation(
        summary = "회원 좋아요 이력",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 응답 (AdminResponse)
        - **`memberLikesPage`**: Page<LikeHistory>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberLikes(AdminRequest adminRequest);

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 AI 사용 이력 페이지네이션 조회 API 추가 (aiUsageType 필터)"
        )
    })
    @Operation(
        summary = "회원 AI 사용 이력",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수)
        - **`aiUsageType`** (Enum, 선택): AI 사용 타입 필터. 미지정 시 전체
        - 페이지네이션 파라미터 동일

        ## 응답 (AdminResponse)
        - **`memberAiUsagePage`**: Page<AiUsageHistory>

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        """
    )
    ResponseEntity<AdminResponse> getMemberAiUsage(AdminRequest adminRequest);
}
