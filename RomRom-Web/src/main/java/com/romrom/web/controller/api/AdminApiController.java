package com.romrom.web.controller.api;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.dto.AdminResponse.AdminLogFileInfo;
import com.romrom.application.service.AdminAlertConfigService;
import com.romrom.application.service.AdminAnnouncementService;
import com.romrom.application.service.AdminAuthService;
import com.romrom.application.service.AdminChatRoomService;
import com.romrom.application.service.AdminDashboardService;
import com.romrom.application.service.AdminItemService;
import com.romrom.application.service.AdminMemberService;
import com.romrom.application.service.AdminReportService;
import com.romrom.application.service.AdminReviewService;
import com.romrom.application.service.AdminTradeService;
import com.romrom.application.service.LogFileService;
import com.romrom.application.service.SystemConfigService;
import com.romrom.common.service.SseLogBroadcaster;
import com.romrom.item.service.ItemService;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final AdminDashboardService adminDashboardService;
    private final AdminReviewService adminReviewService;
    private final AdminChatRoomService adminChatRoomService;
    private final LogFileService logFileService;
    private final SseLogBroadcaster sseLogBroadcaster;

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

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 714, description = "대시보드 통계에 거래 상태별 카운트(tradeStatusCounts, 데이터 주도) + 신규 후기 카운트(newReviewCount) 추가, startDate/endDate 기간 필터 지원"),
    })
    @Operation(
        summary = "관리자 대시보드 통계 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`startDate`** (String): 기간 시작 (yyyy-MM-dd). 없으면 전체 누적
        - **`endDate`** (String): 기간 종료 (yyyy-MM-dd, 해당 일자 23:59:59 까지 포함)

        ## 반환값 (AdminResponse.dashboardStats)
        - **`totalMembers`**: 전체 활성 회원 수 (기간 무관 현재값)
        - **`totalItems`**: 전체 활성 물품 수 (기간 무관 현재값)
        - **`ongoingTrades`**: 진행중 거래 건수 (기간 무관 현재값)
        - **`pendingReports`**: 미처리 신고 건수 (기간 무관 현재값)
        - **`tradeStatusCounts`**: 거래 상태별 건수 Map (모든 TradeStatus 키 포함, 0건도 노출). 기간 필터 적용 시 해당 기간 집계
        - **`newReviewCount`**: 신규 후기 건수. 기간 필터 적용 시 해당 기간 작성 후기 수
        """
    )
    @PostMapping(value = "/dashboard/stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDashboardStats(@ModelAttribute AdminRequest request) {
        AdminResponse.AdminDashboardStats tradeStatusStats = adminDashboardService.getTradeStatusStats(request);
        return ResponseEntity.ok(AdminResponse.builder()
            .dashboardStats(AdminResponse.AdminDashboardStats.builder()
                .totalMembers(memberService.countActiveMembers())
                .totalItems(itemService.countActiveItems())
                .ongoingTrades(itemService.countOngoingTrades())
                .pendingReports(adminReportService.countPendingReports())
                .tradeStatusCounts(tradeStatusStats.getTradeStatusCounts())
                .newReviewCount(tradeStatusStats.getNewReviewCount())
                .build())
            .build());
    }

    @PostMapping(value = "/dashboard/recent-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberService.getRecentMembersForAdmin(8));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 714, description = "대시보드 최근 교환완료(TRADED) 거래 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 대시보드 최근 교환완료 거래 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 반환값 (AdminResponse.recentTrades)
        - 최근 교환완료(TRADED) 거래 최신 8건 (takeItem/giveItem 및 각 소유 회원 정보 포함, updatedDate 내림차순)
        """
    )
    @PostMapping(value = "/dashboard/recent-trades", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentTrades(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminDashboardService.getRecentTrades(8));
    }

    @PostMapping(value = "/dashboard/recent-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getRecentItems(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminItemService.getRecentItemsForAdmin(8));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 793, description = "대시보드 동접자(온라인 사용자) 조회 API 추가: 앱 전체 동접(Redis heartbeat, 최근 5분 윈도우) + 채팅 온라인(현재 채팅방 접속 중) 두 지표"),
    })
    @Operation(
        summary = "관리자 대시보드 동접자(온라인 사용자) 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 동작 설명
        모바일 앱은 HTTP 요청이 stateless라 "현재 연결 여부"를 직접 알 수 없으므로,
        Last-Seen + 시간 윈도우(5분) 근사 방식으로 동접자를 집계한다.
        - 인증된 모든 API 요청이 Redis Sorted Set에 heartbeat로 기록되고, 조회 시 5분 초과 항목을 청소한 뒤 남은 수를 센다.
        - 다중 인스턴스(블루그린)에서도 공유 Redis로 합산이 정확하다.

        ## 반환값 (AdminResponse)
        - **`onlineMemberCount`** (Long): 앱 전체 동접자 수 (최근 5분 내 인증 API 호출한 고유 회원 수)
        - **`chatOnlineMemberCount`** (Long): 채팅 온라인 회원 수 (현재 채팅방 접속 중인 고유 회원 수)
        """
    )
    @PostMapping(value = "/dashboard/online-stats", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getOnlineStats(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminDashboardService.getOnlineStats());
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
        - INVALID_REQUEST (400): 삭제 사유가 누락됨
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
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 712, description = "관리자 화면이 노출 차단 상태를 읽지 못하던 문제 수정: Item.isAdminHidden 의 @JsonIgnore 제거(응답 노출), adminHideReason 은 @JsonIgnore 유지하고 itemDetail 에 별도 노출"),
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 712, description = "관리자 물품 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 물품 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`itemId`** (UUID, 필수): 조회할 물품 ID

        ## 반환값 (AdminResponse.itemDetail)
        - **`item`**: 물품 기본 정보 (이미지 목록 + `isAdminHidden` 노출 차단 여부 포함)
        - **`adminHideReason`**: 관리자 노출 차단 사유 (내부용, Item 에는 @JsonIgnore 라 별도 노출)
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
        - **`itemName`** (String, 선택): 변경할 물품명
        - **`itemDescription`** (String, 선택): 변경할 물품 설명
        - **`itemCategory`** (ItemCategory, 선택): 변경할 카테고리
        - **`itemCondition`** (ItemCondition, 선택): 변경할 물품 상태
        - **`price`** (Integer, 선택): 변경할 가격

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
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 710, description = "거래 상세 응답에 채팅 전체 내역(chatMessages) 추가 - 관리자 분쟁/신고 추적용"),
        @ApiChangeLog(date = "2026.05.23", author = Author.BAEKJIHOON, issueNumber = 710, description = "관리자 거래 상세 조회 API 추가"),
    })
    @Operation(
        summary = "관리자 거래 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeRequestHistoryId`** (UUID, 필수): 조회할 거래 이력 ID

        ## 반환값 (AdminResponse.tradeDetail)
        - **`tradeRequestHistory`**: 거래 이력 (takeItem/giveItem 및 각 소유 회원 정보 포함, 각 물품의 itemImages 포함)
        - **`chatRoom`**: 연결된 채팅방 (CHATTING 이상 상태에서만 존재, PENDING이면 null)
        - **`chatMessages`**: 거래 채팅 전체 내역 (시간순 오름차순, 채팅방 없으면 빈 리스트). senderId/recipientId/content/imageUrls/type/createdDate 포함

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

    // ==================== Reviews ====================

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 목록 조회 API 추가 (평점/기간/블라인드여부 필터)"),
    })
    @Operation(
        summary = "관리자 후기 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`tradeReviewRating`** (TradeReviewRating): 평점 필터 (BAD/GOOD/GREAT, 미입력=전체)
        - **`isBlindedFilter`** (Boolean): 블라인드 여부 필터 (true=블라인드만, false=정상만, 미입력=전체)
        - **`startDate`** / **`endDate`** (String, yyyy-MM-dd): 작성일 기간 필터
        - **`pageNumber`** / **`pageSize`** / **`sortBy`** / **`sortDirection`**: 페이지네이션

        ## 반환값 (AdminResponse)
        - **`reviews`**: 페이지네이션된 후기 목록 (작성자/대상자/거래 + blindInfo 포함)
        - **`totalCount`** / **`totalPages`** / **`totalElements`** / **`currentPage`**: 페이지 정보
        """
    )
    @PostMapping(value = "/reviews/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getReviews(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.getReviewsForAdmin(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 블라인드 처리 API 추가 (처리자/시각 기록)"),
    })
    @Operation(
        summary = "관리자 후기 블라인드 처리",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeReviewId`** (UUID, 필수): 블라인드 처리할 후기 ID
        - **`blindReason`** (String, 선택): 블라인드 사유

        ## 동작 설명
        - 실데이터 삭제 없이 일반 사용자 조회에서 후기 내용을 "관리자에 의해 블라인드 처리된 후기입니다"로 치환합니다.
        - 처리한 관리자(blindByAdminId)와 처리 시각(blindDate)을 기록합니다.

        ## 에러코드
        - TRADE_REVIEW_NOT_FOUND (404): 해당 후기가 존재하지 않음
        """
    )
    @PostMapping(value = "/reviews/blind", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> blindReview(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.blindReview(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.04", author = Author.SUHSAECHAN, issueNumber = 771, description = "관리자 후기 블라인드 해제 API 추가"),
    })
    @Operation(
        summary = "관리자 후기 블라인드 해제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`tradeReviewId`** (UUID, 필수): 블라인드를 해제할 후기 ID

        ## 동작 설명
        - 블라인드 처리 정보(isBlinded/사유/처리자/시각)를 초기화하여 후기를 다시 노출합니다.

        ## 에러코드
        - TRADE_REVIEW_NOT_FOUND (404): 해당 후기가 존재하지 않음
        """
    )
    @PostMapping(value = "/reviews/unblind", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> unblindReview(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminReviewService.unblindReview(request));
    }

    // ==================== Chat Rooms ====================

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 soft-delete 채팅방 목록 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data, 모두 선택)
        - **`pageNumber`** (Integer, 기본값 0): 페이지 번호
        - **`pageSize`** (Integer, 기본값 20): 페이지 크기
        - **`sortDirection`** (Sort.Direction, 기본값 DESC): deletedAt 기준 정렬 방향

        ## 동작 설명
        - soft-delete(삭제 시각 deletedAt 존재)된 청소 대기 채팅방만 조회합니다.
        - 정렬 기준은 deletedAt 으로 고정됩니다 (sortBy 무시).

        ## 반환값 (AdminResponse)
        - **`deletedChatRooms`**: 페이지네이션된 soft-delete 채팅방 목록
        - **`totalCount`**: 전체 청소 대기 채팅방 건수
        """
    )
    @PostMapping(value = "/chat-rooms/deleted-list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getDeletedChatRooms(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getDeletedChatRooms(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 상세 조회",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 조회할 채팅방 ID

        ## 반환값 (AdminResponse)
        - **`chatRoom`**: 채팅방 엔티티 정보
        - **`chatMessages`**: 해당 채팅방의 전체 메시지 목록 (작성일 오름차순)

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        """
    )
    @PostMapping(value = "/chat-rooms/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getChatRoomDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminChatRoomService.getChatRoomDetail(request));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 백업 추출 (다운로드)",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 추출할 채팅방 ID

        ## 동작 설명
        - 채팅방(엔티티 + 메시지)을 JSON 으로 직렬화한 뒤 gzip 으로 압축한 파일을 다운로드합니다.
        - 응답은 `application/octet-stream` 이며 파일명은 `chat-room_{chatRoomId}.json.gz` 입니다.

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        - CHATROOM_EXPORT_FAILED (500): 추출/압축 처리 중 오류 발생
        """
    )
    @PostMapping(value = "/chat-rooms/export", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Resource> exportChatRoom(@ModelAttribute AdminRequest request) {
        byte[] gzipBytes = adminChatRoomService.exportChatRoom(request);
        String downloadFileName = "chat-room_" + request.getChatRoomId() + ".json.gz";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(gzipBytes.length)
            .body(new ByteArrayResource(gzipBytes));
    }

    @ApiChangeLogs({
        @ApiChangeLog(date = "2026.06.05", author = Author.SUHSAECHAN, issueNumber = 750, description = "채팅방 즉시 물리삭제를 soft delete + 배치 아카이브로 전환, 관리자 채팅방 관리 API 추가"),
    })
    @Operation(
        summary = "관리자 채팅방 즉시 물리 삭제",
        description = """
        ## 인증: **ROLE_ADMIN**

        ## 요청 파라미터 (multipart/form-data)
        - **`chatRoomId`** (UUID, 필수): 즉시 삭제할 채팅방 ID

        ## 동작 설명
        - 삭제 전 반드시 파일로 아카이브(백업)한 뒤 물리 삭제를 수행합니다.
        - 백업에 실패하면 데이터 유실 방지를 위해 삭제를 중단하고 500 을 반환합니다.

        ## 에러코드
        - CHATROOM_NOT_FOUND (404): 해당 chatRoomId의 채팅방이 존재하지 않음
        - CHATROOM_EXPORT_FAILED (500): 백업 실패로 삭제 중단
        """
    )
    @PostMapping(value = "/chat-rooms/force-delete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<Void> forceDeleteChatRoom(@ModelAttribute AdminRequest request) {
        adminChatRoomService.forceDeleteChatRoom(request);
        return ResponseEntity.ok().build();
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

    // ==================== Logs ====================

    // 리버스 프록시(시놀로지 nginx)의 proxy_read_timeout(기본 60초)보다 짧게 설정.
    // 서버가 먼저 깔끔하게 연결을 닫고 FE가 재연결하도록 유도 — 프록시가 중간에 끊는 것보다 끊김 타이밍이 예측 가능.
    private static final long LOG_SSE_TIMEOUT = 50_000L; // 50초
    private static final long LOG_SSE_HEARTBEAT_SECONDS = 10L;

    private static final ScheduledExecutorService logHeartbeatScheduler =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
          Thread heartbeatThread = new Thread(runnable, "admin-log-sse-heartbeat");
          heartbeatThread.setDaemon(true);
          return heartbeatThread;
        });

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
    @PostMapping(value = "/logs/query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryLogs(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 200;
      List<String> logLines = logFileService.readRecentLines(
          lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(logLines).build());
    }

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
    @PostMapping(value = "/logs/errors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> aggregateLogErrors(@ModelAttribute AdminRequest request) {
      int withinMinutes = request.getLogErrorWithinMinutes() != null ? request.getLogErrorWithinMinutes() : 60;
      String errorSortBy = request.getLogErrorSortBy() != null ? request.getLogErrorSortBy() : "count";
      return ResponseEntity.ok(AdminResponse.builder()
          .logErrorSummaries(logFileService.aggregateErrors(withinMinutes, errorSortBy))
          .build());
    }

    @Operation(summary = "관리자 로그 파일 목록 + 용량/디스크 상태", description = "## 인증: **ROLE_ADMIN**\n현재 .log + 과거 .gz 목록, 총 용량, 디스크 여유공간 반환")
    @PostMapping(value = "/logs/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> listLogFiles(@ModelAttribute AdminRequest request) {
      List<AdminLogFileInfo> logFiles = logFileService.listLogFiles();
      return ResponseEntity.ok(AdminResponse.builder()
          .logFiles(logFiles)
          .logFileCount(logFiles.size())
          .logTotalSizeBytes(logFileService.getLogTotalSizeBytes(logFiles))
          .diskFreeBytes(logFileService.getDiskFreeBytes())
          .diskTotalBytes(logFileService.getDiskTotalBytes())
          .build());
    }

    @Operation(summary = "관리자 .gz 로그 조회/검색", description = "## 인증: **ROLE_ADMIN**\n과거 .gz 파일을 서버에서 압축 해제 후 레벨/키워드 필터 적용. logFileName 필수.")
    @PostMapping(value = "/logs/gz-query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> queryGzLog(@ModelAttribute AdminRequest request) {
      int lineCount = request.getLogLineCount() != null ? request.getLogLineCount() : 500;
      List<String> gzLines = logFileService.readGzLines(
          request.getLogFileName(), lineCount, request.getLogLevelFilter(), request.getLogKeyword());
      return ResponseEntity.ok(AdminResponse.builder().logLines(gzLines).build());
    }

    @Operation(summary = "관리자 로그 시간범위 다운로드", description = "## 인증: **ROLE_ADMIN**\n현재 romrom.log에서 최근 range(5m/1h/6h/24h) 라인을 잘라 다운로드")
    @GetMapping(value = "/logs/download")
    @LogMonitor
    public ResponseEntity<byte[]> downloadByTimeRange(@RequestParam("range") String range) {
      Duration duration = switch (range) {
        case "5m" -> Duration.ofMinutes(5);
        case "1h" -> Duration.ofHours(1);
        case "6h" -> Duration.ofHours(6);
        case "24h" -> Duration.ofHours(24);
        default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 range");
      };
      String extractedContent = logFileService.extractByTimeRange(duration);
      String serverStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      String downloadFileName = "romrom-log_" + range + "_" + serverStamp + ".log";
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
          .body(extractedContent.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "관리자 로그 파일 통째 다운로드", description = "## 인증: **ROLE_ADMIN**\nfileName(.log 또는 .gz)을 화이트리스트 검증 후 스트리밍 다운로드")
    @GetMapping(value = "/logs/download-file")
    @LogMonitor
    public ResponseEntity<Resource> downloadLogFile(@RequestParam("fileName") String fileName) {
      Resource logFileResource = logFileService.getLogFileResource(fileName);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
          .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
          .body(logFileResource);
    }

    @Operation(summary = "관리자 실시간 로그 스트림 (SSE)", description = "## 인증: **ROLE_ADMIN** (쿠키 accessToken)\ntail -f 형태 라이브 스트림. 기존 SseLogBroadcaster 재활용, 최대 10 구독자.\n\n## 프록시 대응\n- 응답에 `X-Accel-Buffering: no` 헤더를 추가해 리버스 프록시(nginx) 버퍼링을 비활성화 (즉시 전달).\n- timeout 50초(프록시 read timeout 60초보다 짧게) → 서버가 먼저 닫고 FE가 재연결.\n- 스트림 컨트롤러/서비스 로그는 SseLogAppender 제외목록으로 차단해 피드백 루프 방지. (@LogMonitor 미적용)")
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamLogs() {
      SseEmitter logEmitter = new SseEmitter(LOG_SSE_TIMEOUT);
      boolean isRegistered = sseLogBroadcaster.addSubscriber(logEmitter);
      if (!isRegistered) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "최대 동시 접속 수 초과");
      }
      try {
        logEmitter.send(SseEmitter.event().name("connected").data("connected"));
      } catch (IOException e) {
        sseLogBroadcaster.removeSubscriber(logEmitter);
        logEmitter.complete();
        return sseStreamResponse(logEmitter);
      }
      AtomicReference<ScheduledFuture<?>> heartbeatTaskRef = new AtomicReference<>();
      ScheduledFuture<?> heartbeatTask = logHeartbeatScheduler.scheduleAtFixedRate(() -> {
        try {
          logEmitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException e) {
          ScheduledFuture<?> selfTask = heartbeatTaskRef.get();
          if (selfTask != null) {
            selfTask.cancel(false);
          }
          logEmitter.completeWithError(e);
        }
      }, LOG_SSE_HEARTBEAT_SECONDS, LOG_SSE_HEARTBEAT_SECONDS, TimeUnit.SECONDS);
      heartbeatTaskRef.set(heartbeatTask);

      logEmitter.onCompletion(() -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
      });
      logEmitter.onTimeout(() -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
        logEmitter.complete();
      });
      logEmitter.onError(throwable -> {
        heartbeatTask.cancel(false);
        sseLogBroadcaster.removeSubscriber(logEmitter);
      });

      return sseStreamResponse(logEmitter);
    }

    /**
     * SSE 응답을 리버스 프록시 버퍼링 해제 헤더와 함께 래핑.
     * X-Accel-Buffering: no 가 핵심 — nginx가 이 응답만 버퍼링하지 않고 즉시 전달.
     * "Connection closed before full header was received" 류의 프록시 조기 종료를 방지.
     */
    private ResponseEntity<SseEmitter> sseStreamResponse(SseEmitter sseEmitter) {
      return ResponseEntity.ok()
          .header("X-Accel-Buffering", "no")
          .header(HttpHeaders.CACHE_CONTROL, "no-cache")
          .header(HttpHeaders.CONNECTION, "keep-alive")
          .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
          .body(sseEmitter);
    }
}
