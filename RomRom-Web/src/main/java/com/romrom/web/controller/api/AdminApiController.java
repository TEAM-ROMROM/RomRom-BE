package com.romrom.web.controller.api;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.BulkActionResult;
import com.romrom.application.service.AdminAuthService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import com.romrom.application.service.AdminAlertConfigService;
import com.romrom.application.service.AdminAnnouncementService;
import com.romrom.application.service.AdminReportService;
import com.romrom.application.service.SystemConfigService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminApiController {

    private final AdminAuthService adminAuthService;
    private final AdminItemService adminItemService;
    private final AdminMemberService adminMemberService;
    private final ItemService itemService;
    private final MemberService memberService;
    private final AdminReportService adminReportService;
    private final AdminAnnouncementService adminAnnouncementService;
    private final SystemConfigService systemConfigService;
    private final AdminAlertConfigService adminAlertConfigService;

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminResponse> login(@ModelAttribute AdminRequest request,
                                               HttpServletResponse response) {
        AdminResponse loginResponse = adminAuthService.authenticateWithJwt(request.getUsername(), request.getPassword());

        // accessToken 쿠키 (httpOnly - XSS 방지)
        Cookie accessTokenCookie = new Cookie("accessToken", loginResponse.getAccessToken());
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setSecure(sslEnabled);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(60 * 60); // 1시간
        response.addCookie(accessTokenCookie);

        // refreshToken 쿠키 (httpOnly - XSS 방지)
        Cookie refreshTokenCookie = new Cookie("refreshToken", loginResponse.getRefreshToken());
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(sslEnabled);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(60 * 60 * 24 * 7); // 7일
        response.addCookie(refreshTokenCookie);

        // JavaScript 로그인 상태 확인용 쿠키 (토큰 값 포함X)
        Cookie authStatusCookie = new Cookie("authStatus", "authenticated");
        authStatusCookie.setHttpOnly(false);
        authStatusCookie.setSecure(sslEnabled);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(60 * 60); // accessToken과 동일
        response.addCookie(authStatusCookie);

        log.info("관리자 로그인 성공: {}", request.getUsername());
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping(value = "/logout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> logout(@CookieValue(value = "refreshToken", required = false) String refreshTokenFromCookie,
                                      HttpServletResponse response) {
        if (refreshTokenFromCookie != null) {
            adminAuthService.logout(refreshTokenFromCookie);
        }

        // 쿠키 삭제
        Cookie accessTokenCookie = new Cookie("accessToken", null);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge(0);
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", null);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(0);
        response.addCookie(refreshTokenCookie);

        Cookie authStatusCookie = new Cookie("authStatus", null);
        authStatusCookie.setPath("/");
        authStatusCookie.setMaxAge(0);
        response.addCookie(authStatusCookie);

        log.info("관리자 로그아웃 완료");
        return ResponseEntity.ok().build();
    }

    // ==================== Dashboard ====================

    @PostMapping(value = "/dashboard/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDashboardStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(AdminResponse.builder()
            .dashboardStats(AdminResponse.AdminDashboardStats.builder()
                .totalMembers(memberService.countActiveMembers())
                .totalItems(itemService.countActiveItems())
                .ongoingTrades(itemService.countOngoingTrades())
                .pendingReports(adminReportService.countPendingReports())
                .build())
            .build());
    }

    @PostMapping(value = "/dashboard/recent-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getRecentMembersForAdmin(8));
    }

    @PostMapping(value = "/dashboard/recent-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getRecentItemsForAdmin(8));
    }

    // ==================== Items ====================

    @PostMapping(value = "/items/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemsForAdmin(request));
    }

    @PostMapping(value = "/items/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> deleteItem(@ModelAttribute AdminRequest request) {
        itemService.deleteItemByAdmin(request.getItemId(), request.getItemAdminDeleteReason(), request.getItemAdminDeleteDetail());
        return ResponseEntity.ok().build();
    }

    // ==================== Members ====================

    @PostMapping(value = "/members/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMembersForAdmin(request));
    }

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
    @PostMapping(value = "/members/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMemberDetailForAdmin(request));
    }

    // ==================== Member 360 Sub-lists ====================

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
    @PostMapping(value = "/members/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItems(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemsPage(adminMemberService.listOwnedItems(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/trades", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberTrades(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberTradesPage(adminMemberService.listTrades(adminRequest.getMemberId(), adminRequest.getTradeSide(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/chat-rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberChatRooms(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberChatRoomsPage(adminMemberService.listChatRooms(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/reports-received-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItemReportsReceived(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemReportsReceivedPage(adminMemberService.listItemReportsReceived(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/reports-received-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberMemberReportsReceived(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberMemberReportsReceivedPage(adminMemberService.listMemberReportsReceived(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/reports-filed-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItemReportsFiled(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemReportsFiledPage(adminMemberService.listItemReportsFiled(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/reports-filed-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberMemberReportsFiled(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberMemberReportsFiledPage(adminMemberService.listMemberReportsFiled(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/notification-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberNotificationHistory(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberNotificationHistoryPage(adminMemberService.listNotifications(adminRequest.getMemberId(), pageable))
            .build());
    }

    @ApiChangeLogs({
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
    @PostMapping(value = "/members/login-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberLoginHistory(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberLoginHistoryPage(adminMemberService.listLoginHistory(adminRequest.getMemberId(), adminRequest.getLoginResult(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/likes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberLikes(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberLikesPage(adminMemberService.listLikes(adminRequest.getMemberId(), pageable))
            .build());
    }

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
    @PostMapping(value = "/members/ai-usage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberAiUsage(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberAiUsagePage(adminMemberService.listAiUsage(adminRequest.getMemberId(), adminRequest.getAiUsageType(), pageable))
            .build());
    }

    // ==================== Member Actions ====================

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 강제 탈퇴 API 추가"
        )
    })
    @Operation(
        summary = "회원 강제 탈퇴",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 강제 탈퇴 대상
        - **`forceWithdrawReason`** (String, 필수): 탈퇴 사유

        ## 응답 (AdminResponse)
        - 빈 응답 (HTTP 200 OK)

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - ADMIN_SELF_ACTION_FORBIDDEN (403): 본인 계정 강제 탈퇴 시도
        - MEMBER_ALREADY_DELETED (409): 이미 탈퇴된 회원
        """
    )
    @PostMapping(value = "/members/force-withdraw", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> forceWithdrawMember(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        adminMemberService.forceWithdrawMember(
            adminRequest.getMemberId(),
            adminRequest.getForceWithdrawReason(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder().build());
    }

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "회원 보유 물품 일괄 삭제 API 추가"
        )
    })
    @Operation(
        summary = "회원 보유 물품 일괄 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 물품 소유 회원
        - **`itemIds`** (List<UUID>, 필수): 삭제 대상 물품 ID 목록
        - **`itemAdminDeleteReason`** (String, 선택): 삭제 사유

        ## 응답 (AdminResponse)
        - **`bulkActionResults`**: List<BulkActionResult> — 각 itemId별 성공/실패 결과

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - INVALID_REQUEST (400): itemIds 누락
        """
    )
    @PostMapping(value = "/members/items/bulk-delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> bulkDeleteMemberItems(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        List<BulkActionResult> bulkDeleteResults = adminMemberService.bulkDeleteItems(
            adminRequest.getMemberId(),
            adminRequest.getItemIds(),
            adminRequest.getItemAdminDeleteReason(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder()
            .bulkActionResults(bulkDeleteResults)
            .build());
    }

    @ApiChangeLogs({
        @ApiChangeLog(
            date = "2026.05.21",
            author = Author.SUHSAECHAN,
            issueNumber = 708,
            description = "관리자 → 회원 알림 발송 API 추가"
        )
    })
    @Operation(
        summary = "관리자 알림 발송 (개별 회원)",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`memberId`** (UUID, 필수): 알림 수신 회원
        - **`adminNotificationTitle`** (String, 필수): 알림 제목
        - **`adminNotificationContent`** (String, 필수): 알림 본문
        - **`adminNotificationType`** (String, 선택): 알림 타입(NotificationType enum 이름)

        ## 응답 (AdminResponse)
        - 빈 응답 (HTTP 200 OK)

        ## 에러코드
        - MEMBER_NOT_FOUND (404)
        - INVALID_REQUEST (400): 제목/본문 누락
        """
    )
    @PostMapping(value = "/members/send-notification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> sendNotificationToMember(
        @ModelAttribute AdminRequest adminRequest,
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        adminMemberService.sendNotificationToMember(
            adminRequest.getMemberId(),
            adminRequest.getAdminNotificationTitle(),
            adminRequest.getAdminNotificationContent(),
            adminRequest.getAdminNotificationType(),
            principal.getMember().getMemberId()
        );
        return ResponseEntity.ok(AdminResponse.builder().build());
    }

    /**
     * 회원 360 sub-list 페이지네이션 헬퍼.
     * AdminRequest의 pageNumber/pageSize/sortBy/sortDirection을 PageRequest로 변환.
     */
    private Pageable buildMemberSubListPageable(AdminRequest adminRequest) {
        return PageRequest.of(
            adminRequest.getPageNumber(),
            adminRequest.getPageSize(),
            Sort.by(adminRequest.getSortDirection(), adminRequest.getSortBy())
        );
    }

    @PostMapping(value = "/members/status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMemberStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.updateMemberStatusForAdmin(request));
    }

    @PostMapping(value = "/members/suspend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> suspendMember(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.suspendMember(request));
    }

    @PostMapping(value = "/members/unsuspend", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> unsuspendMember(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.unsuspendMember(request));
    }

    // ==================== Sanctions ====================

    @PostMapping(value = "/members/sanction-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberSanctionHistory(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMemberSanctionHistory(request));
    }

    @PostMapping(value = "/sanctions/history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAllSanctionHistory(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getAllSanctionHistory(request));
    }

    // ==================== Reports ====================

    @PostMapping(value = "/reports/item-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReports(request));
    }

    @PostMapping(value = "/reports/member-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReports(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReports(request));
    }

    @PostMapping(value = "/reports/item-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getItemReportDetail(request));
    }

    @PostMapping(value = "/reports/member-detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberReportDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getMemberReportDetail(request));
    }

    @PostMapping(value = "/reports/update-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateReportStatus(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.updateStatus(request));
    }

    @PostMapping(value = "/reports/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getReportStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReportService.getStats());
    }

    // ==================== Announcements ====================

    @PostMapping(value = "/announcements/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAnnouncements(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.getAnnouncements(request));
    }

    @PostMapping(value = "/announcements/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> createAnnouncement(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.createAnnouncement(request));
    }

    @PostMapping(value = "/announcements/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> deleteAnnouncement(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAnnouncementService.deleteAnnouncement(request));
    }

    // ==================== Config ====================

    @PostMapping(value = "/config/ai/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAiConfig());
    }

    @PostMapping(value = "/config/ai/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAiConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAiConfig(request));
    }

    @PostMapping(value = "/config/cache/reload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> reloadCache(@ModelAttribute AdminRequest request) {
        systemConfigService.reloadCache();
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/config/app-version/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getAppVersionConfig());
    }

    @PostMapping(value = "/config/app-version/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAppVersionConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateAppVersionConfig(request));
    }

    @PostMapping(value = "/config/ugc-filter/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getUgcFilterConfig());
    }

    @PostMapping(value = "/config/ugc-filter/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateUgcFilterConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateUgcFilterConfig(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 조회 API 구현"),
    })
    @Operation(
        summary = "서버 점검 모드 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`maintenanceEnabled`**: 점검 모드 활성화 여부 ("true"/"false")
        - **`maintenanceMessage`**: 점검 안내 메시지
        - **`maintenanceEndTime`**: 점검 예상 종료 시간 (ISO 8601, 없으면 빈 문자열)
        """
    )
    @PostMapping(value = "/config/maintenance/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMaintenanceConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getMaintenanceConfig());
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.02", author = Author.SUHSAECHAN, issueNumber = 673, description = "서버 점검 모드 업데이트 API 구현"),
    })
    @Operation(
        summary = "서버 점검 모드 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`maintenanceEnabled`** (String, 선택): "true" 또는 "false"
        - **`maintenanceMessage`** (String, 선택): 점검 안내 메시지
        - **`maintenanceEndTime`** (String, 선택): 점검 예상 종료 시간 (ISO 8601, 예: 2026-05-02T15:00:00)

        ## 동작 설명
        - null인 필드는 무시하고 기존 설정 유지
        - maintenanceEnabled를 "true"로 설정하면 즉시 점검 모드 활성화
        - 점검 중에는 /api/admin/**, /api/app/version/check, /actuator/** 외 모든 API 503 반환

        ## 에러코드
        - INVALID_REQUEST (400): maintenanceEnabled가 "true"/"false" 외의 값
        """
    )
    @PostMapping(value = "/config/maintenance/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateMaintenanceConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateMaintenanceConfig(request));
    }

    // ==================== Alert Config ====================

    @PostMapping(value = "/alert-config/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.getAlertConfig());
    }

    @PostMapping(value = "/alert-config/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.updateAlertConfig(request));
    }
}
