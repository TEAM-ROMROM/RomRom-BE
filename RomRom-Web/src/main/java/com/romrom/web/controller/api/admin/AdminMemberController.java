package com.romrom.web.controller.api.admin;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.application.service.AdminMemberQueryService;
import lombok.RequiredArgsConstructor;
import me.suhsaechan.suhlogger.annotation.LogMonitor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 회원 조회 API: 목록, 상세(360 뷰), 회원 360 하위 목록 페이지
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminMemberController implements AdminMemberControllerDocs {

    private final AdminMemberQueryService adminMemberQueryService;

    @Override
    @PostMapping(value = "/members/list", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMembers(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberQueryService.getMembersForAdmin(request));
    }

    @Override
    @PostMapping(value = "/members/detail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberDetail(@ModelAttribute AdminRequest request) {
        return ResponseEntity.ok(adminMemberQueryService.getMemberDetailForAdmin(request));
    }

    @Override
    @PostMapping(value = "/members/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItems(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemsPage(adminMemberQueryService.listOwnedItems(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/trades", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberTrades(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberTradesPage(adminMemberQueryService.listTrades(adminRequest.getMemberId(), adminRequest.getTradeSide(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/chat-rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberChatRooms(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberChatRoomsPage(adminMemberQueryService.listChatRooms(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/reports-received-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItemReportsReceived(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemReportsReceivedPage(adminMemberQueryService.listItemReportsReceived(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/reports-received-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberMemberReportsReceived(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberMemberReportsReceivedPage(adminMemberQueryService.listMemberReportsReceived(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/reports-filed-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberItemReportsFiled(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberItemReportsFiledPage(adminMemberQueryService.listItemReportsFiled(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/reports-filed-members", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberMemberReportsFiled(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberMemberReportsFiledPage(adminMemberQueryService.listMemberReportsFiled(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/notification-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberNotificationHistory(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberNotificationHistoryPage(adminMemberQueryService.listNotifications(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/login-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberLoginHistory(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberLoginHistoryPage(adminMemberQueryService.listLoginHistory(adminRequest.getMemberId(), adminRequest.getLoginResult(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/likes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberLikes(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberLikesPage(adminMemberQueryService.listLikes(adminRequest.getMemberId(), pageable))
            .build());
    }

    @Override
    @PostMapping(value = "/members/ai-usage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @LogMonitor
    public ResponseEntity<AdminResponse> getMemberAiUsage(@ModelAttribute AdminRequest adminRequest) {
        Pageable pageable = buildMemberSubListPageable(adminRequest);
        return ResponseEntity.ok(AdminResponse.builder()
            .memberAiUsagePage(adminMemberQueryService.listAiUsage(adminRequest.getMemberId(), adminRequest.getAiUsageType(), pageable))
            .build());
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
}
