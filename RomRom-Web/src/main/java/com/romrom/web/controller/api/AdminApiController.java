package com.romrom.web.controller.api;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminAlertConfigService;
import com.romrom.application.service.AdminAnnouncementService;
import com.romrom.application.service.AdminAuthService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberService;
import com.romrom.application.service.AdminReportService;
import com.romrom.application.service.AdminTradeService;
import com.romrom.application.service.SystemConfigService;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@Slf4j
public class AdminApiController {

    private final AdminAuthService adminAuthService;
    private final AdminItemService adminItemService;
    private final AdminMemberService adminMemberService;
    private final AdminTradeService adminTradeService;
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

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "FK 제약 위반 방지: chat_room → trade_request_history cascade 삭제 순서 보장"),
    })
    @Operation(
        summary = "관리자 물품 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 삭제할 물품 ID
        - **`itemAdminDeleteReason`** (ItemAdminDeleteReason, 필수): 삭제 사유
        - **`itemAdminDeleteDetail`** (String, 선택): 삭제 사유 상세 설명

        ## 동작 설명
        - FK 제약 위반 방지를 위해 cascade 순서 보장: chat_room → trade_request_history → item(soft delete)
        - 영향받는 회원에게 FCM 삭제 알림 발송

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> deleteItem(@ModelAttribute AdminRequest request) {
        adminItemService.deleteItemByAdmin(request.getItemId(), request.getItemAdminDeleteReason(), request.getItemAdminDeleteDetail());
        return ResponseEntity.ok().build();
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.11", author = Author.KIMNAYOUNG, issueNumber = 686, description = "관리자 물품 거래 상태 변경 API" +
          " 추가"),
    })
    @Operation(
        summary = "관리자 물품 거래 상태 변경",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 거래 상태를 변경할 물품 ID
        - **`itemStatus`** (ItemStatus, 필수): 변경할 거래 상태 (AVAILABLE / EXCHANGED)

        ## 동작 설명
        - 해당 itemId의 물품 거래 상태를 요청한 itemStatus로 변경합니다.

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/update-status", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> updateItemStatus(@ModelAttribute AdminRequest request) {
        adminItemService.updateItemStatus(request);
        return ResponseEntity.ok().build();
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 조회할 물품 ID

        ## 반환값 (AdminResponse.itemDetail)
        - **`item`**: 물품 기본 정보 (이미지 목록 포함)
        - **`tradeHistories`**: 해당 물품이 포함된 거래 이력 목록
        - **`itemReports`**: 해당 물품에 대한 신고 이력 목록

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getItemDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getItemDetail(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 카테고리/가격 수정 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 카테고리/가격 수정",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 수정할 물품 ID
        - **`itemCategory`** (ItemCategory, 선택): 변경할 카테고리 (null이면 유지)
        - **`price`** (Integer, 선택): 변경할 가격 (null이면 유지)

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> updateItem(@ModelAttribute AdminRequest request) {
        adminItemService.updateItem(request);
        return ResponseEntity.ok().build();
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 노출 차단 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 노출 차단",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 노출 차단할 물품 ID
        - **`adminHideReason`** (String, 선택): 차단 사유 (내부용)

        ## 동작 설명
        - 실데이터 삭제 없이 일반 사용자 조회/검색에서 해당 물품을 제외합니다.

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/hide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> hideItem(@ModelAttribute AdminRequest request) {
        adminItemService.hideItem(request);
        return ResponseEntity.ok().build();
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 노출 차단 해제 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 노출 차단 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 노출 차단을 해제할 물품 ID

        ## 에러코드
        - ITEM_NOT_FOUND (404): 해당 itemId의 물품이 존재하지 않음
        """
    )
    @PostMapping(value = "/items/unhide", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> unhideItem(@ModelAttribute AdminRequest request) {
        adminItemService.unhideItem(request);
        return ResponseEntity.ok().build();
    }

    // ==================== Trades ====================

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 목록 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeStatus`** (TradeStatus, 선택): 거래 상태 필터 (PENDING/CHATTING/TRADE_COMPLETE_REQUESTED/TRADED/CANCELED, null이면 전체)
        - **`searchKeyword`** (String, 선택): takeItem/giveItem 물품명 또는 양쪽 회원 닉네임 검색
        - **`startDate`** (String, 선택): 등록일 시작 (yyyy-MM-dd)
        - **`endDate`** (String, 선택): 등록일 종료 (yyyy-MM-dd)
        - **`pageNumber`** (Integer, 선택, 기본값 0): 페이지 번호
        - **`pageSize`** (Integer, 선택, 기본값 20): 페이지 크기
        - **`sortBy`** (String, 선택, 기본값 createdDate): 정렬 필드
        - **`sortDirection`** (Sort.Direction, 선택, 기본값 DESC): 정렬 방향

        ## 반환값 (AdminResponse)
        - **`trades`**: 페이지네이션된 거래 이력 목록 (takeItem/giveItem/회원 정보 포함)
        - **`totalCount`**: 전체 거래 건수
        - **`totalPages`**: 전체 페이지 수
        - **`totalElements`**: 전체 요소 수
        - **`currentPage`**: 현재 페이지
        """
    )
    @PostMapping(value = "/trades/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getTrades(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminTradeService.getTradesForAdmin(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 조회할 거래 이력 ID

        ## 반환값 (AdminResponse.tradeDetail)
        - **`tradeRequestHistory`**: 거래 이력 (takeItem/giveItem 및 각 소유 회원 정보 포함)
        - **`chatRoom`**: 연결된 채팅방 (CHATTING 이상 상태에서만 존재, PENDING이면 null)

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        """
    )
    @PostMapping(value = "/trades/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getTradeDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminTradeService.getTradeDetail(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 강제 취소 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 강제 취소",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 강제 취소할 거래 이력 ID
        - **`adminTradeForceReason`** (String, 선택): 강제 취소 사유 (내부용)

        ## 동작 설명
        - PENDING/CHATTING/TRADE_COMPLETE_REQUESTED 상태의 거래를 CANCELED로 변경합니다.
        - 기존 cancelTradeRequest 흐름과 동일하게 상태 변경만 수행합니다.

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        - TRADE_ALREADY_COMPLETED (409): 이미 완료된 거래 (TRADED 상태)
        """
    )
    @PostMapping(value = "/trades/force-cancel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceTradeCancel(@ModelAttribute AdminRequest request) {
        adminTradeService.forceCancel(request);
        return ResponseEntity.ok().build();
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 강제 완료 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 강제 완료",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 강제 완료할 거래 이력 ID
        - **`adminTradeForceReason`** (String, 선택): 강제 완료 사유 (내부용)

        ## 동작 설명
        - CHATTING/TRADE_COMPLETE_REQUESTED 상태의 거래를 TRADED로 변경합니다.
        - 양쪽 물품(takeItem/giveItem) 상태를 EXCHANGED로 처리합니다.
        - 채팅방이 있으면 "운영자에 의해 교환 완료 처리되었습니다." 시스템 메시지를 전송합니다.

        ## 에러코드
        - TRADE_REQUEST_NOT_FOUND (404): 해당 거래 이력이 존재하지 않음
        - TRADE_ALREADY_COMPLETED (409): 이미 완료된 거래 (TRADED 상태)
        - INVALID_REQUEST (400): PENDING 상태 거래 (채팅방 없음)
        """
    )
    @PostMapping(value = "/trades/force-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceTradeComplete(@ModelAttribute AdminRequest request) {
        adminTradeService.forceComplete(request);
        return ResponseEntity.ok().build();
    }

    // ==================== Members ====================

    @PostMapping(value = "/members/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMembersForAdmin(request));
    }

    @PostMapping(value = "/members/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getMemberDetailForAdmin(request));
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

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 조회 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`imageCompressSkipContentType`**: 압축 스킵 대상 contentType (기본 image/webp)
        - **`imageCompressSkipMaxSizeBytes`**: 압축 스킵 최대 용량(byte, 기본 512000)
        - **`imageUploadParallelPoolSize`**: 업로드 병렬 스레드풀 크기 (기본 8, 재시작 반영)
        """
    )
    @PostMapping(value = "/config/image/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.getImageConfig());
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.28", author = Author.SUHSAECHAN, issueNumber = 733, description = "이미지 조건부 압축/업로드 병렬화 설정 업데이트 API 구현"),
    })
    @Operation(
        summary = "이미지 압축/업로드 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`imageCompressSkipContentType`** (String): 압축 스킵 대상 contentType
        - **`imageCompressSkipMaxSizeBytes`** (String): 압축 스킵 최대 용량(byte, 0 이상 정수)
        - **`imageUploadParallelPoolSize`** (String): 업로드 병렬 스레드풀 크기 (양의 정수, 재시작 반영)

        ## 동작 설명
        - null/빈 필드는 무시하고 기존 설정 유지
        - contentType/용량 변경은 Redis 캐시 즉시 반영, 풀 크기는 서버 재시작 시 반영

        ## 에러코드
        - INVALID_REQUEST (400): skipMaxSizeBytes가 음수/비정수, poolSize가 0이하/비정수
        """
    )
    @PostMapping(value = "/config/image/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateImageConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(systemConfigService.updateImageConfig(request));
    }

    // ==================== Alert Config ====================

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "AlertConfig 조회 API Swagger 문서 추가"),
    })
    @Operation(
        summary = "알림 설정 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse)
        - **`alertEmail`**: 알림 수신 이메일
        - **`alertThrottleMinutes`**: 신고 알림 쓰로틀링 시간 (분)
        - **`mailSmtpHost`**: SMTP 서버 호스트
        - **`mailSmtpPort`**: SMTP 서버 포트
        - **`mailSmtpUsername`**: SMTP 발송 계정

        ## 요청 파라미터
        - 없음 (multipart/form-data 형식으로 빈 요청 전송)
        """
    )
    @PostMapping(value = "/alert-config/get", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.getAlertConfig());
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.05.24", author = Author.BAEKJIHOON, issueNumber = 715, description = "AlertConfig 업데이트 API Swagger 문서 추가"),
    })
    @Operation(
        summary = "알림 설정 업데이트",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`alertEmail`** (String, 선택): 알림 수신 이메일
        - **`alertThrottleMinutes`** (Integer, 선택): 신고 알림 쓰로틀링 시간 (분)
        - **`mailSmtpHost`** (String, 선택): SMTP 서버 호스트
        - **`mailSmtpPort`** (Integer, 선택): SMTP 서버 포트
        - **`mailSmtpUsername`** (String, 선택): SMTP 발송 계정
        - **`mailSmtpPassword`** (String, 선택): SMTP 발송 비밀번호

        ## 동작 설명
        - null인 필드는 무시하고 기존 설정 유지
        - 업데이트 후 MailSender 즉시 재로드 (변경된 SMTP 설정으로 실제 메일 발송)

        ## 에러코드
        - 없음 (null 필드는 조용히 무시)
        """
    )
    @PostMapping(value = "/alert-config/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> updateAlertConfig(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminAlertConfigService.updateAlertConfig(request));
    }
}
