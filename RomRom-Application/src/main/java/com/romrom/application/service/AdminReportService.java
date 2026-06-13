package com.romrom.application.service;

import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.ItemAdminDeleteReason;
import com.romrom.common.constant.ResolveAction;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.service.ItemService;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ItemReport;
import com.romrom.report.entity.MemberReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportService {

  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;
  // #709 원스톱 처리: 정지/삭제 액션 재사용
  private final AdminMemberService adminMemberService;
  private final ItemService itemService;

  @Transactional(readOnly = true)
  public AdminResponse getItemReports(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    Page<ItemReport> page = request.getReportStatus() != null
        ? itemReportRepository.findByStatusOrderByCreatedDateDesc(request.getReportStatus(), pageable)
        : itemReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .itemReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberReports(AdminRequest request) {
    Pageable pageable = PageRequest.of(request.getPageNumber(), request.getPageSize());

    Page<MemberReport> page = request.getReportStatus() != null
        ? memberReportRepository.findByStatusOrderByCreatedDateDesc(request.getReportStatus(), pageable)
        : memberReportRepository.findAllByOrderByCreatedDateDesc(pageable);

    return AdminResponse.builder()
        .memberReports(page.getContent())
        .totalPages(page.getTotalPages())
        .totalElements(page.getTotalElements())
        .currentPage(page.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getItemReportDetail(AdminRequest request) {
    ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));

    return AdminResponse.builder()
        .itemReport(itemReport)
        .reportResolveDetail(buildItemReportResolveDetail(itemReport))
        .build();
  }

  @Transactional(readOnly = true)
  public AdminResponse getMemberReportDetail(AdminRequest request) {
    MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
        .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));

    return AdminResponse.builder()
        .memberReport(memberReport)
        .reportResolveDetail(buildMemberReportResolveDetail(memberReport))
        .build();
  }

  /**
   * 물품 신고 상세 요약 빌더 (#709)
   * - 피신고자 = 물품 소유자(item.member), 동일 피신고자 누적 신고 = 물품신고 + 회원신고 합산
   */
  private AdminResponse.ReportResolveDetail buildItemReportResolveDetail(ItemReport itemReport) {
    Item reportedItem = itemReport.getItem();
    Member reportedMember = (reportedItem != null) ? reportedItem.getMember() : null;

    return AdminResponse.ReportResolveDetail.builder()
        .reportType(ReportType.ITEM.name())
        .reportedMemberId(reportedMember != null ? reportedMember.getMemberId() : null)
        .reportedMemberNickname(reportedMember != null ? reportedMember.getNickname() : null)
        .reportedMemberAccountStatus(reportedMember != null && reportedMember.getAccountStatus() != null
            ? reportedMember.getAccountStatus().name() : null)
        .reportedMemberSuspended(reportedMember != null
            && reportedMember.getAccountStatus() == com.romrom.common.constant.AccountStatus.SUSPENDED_ACCOUNT)
        .reportedItemId(reportedItem != null ? reportedItem.getItemId() : null)
        .reportedItemName(reportedItem != null ? reportedItem.getItemName() : null)
        .reportedItemStatus(reportedItem != null && reportedItem.getItemStatus() != null
            ? reportedItem.getItemStatus().name() : null)
        .reportedMemberTotalReportCount(countTotalReportsAgainst(reportedMember))
        .build();
  }

  /**
   * 회원 신고 상세 요약 빌더 (#709)
   * - 피신고자 = targetMember, 동일 피신고자 누적 신고 = 물품신고 + 회원신고 합산
   */
  private AdminResponse.ReportResolveDetail buildMemberReportResolveDetail(MemberReport memberReport) {
    Member reportedMember = memberReport.getTargetMember();

    return AdminResponse.ReportResolveDetail.builder()
        .reportType(ReportType.MEMBER.name())
        .reportedMemberId(reportedMember != null ? reportedMember.getMemberId() : null)
        .reportedMemberNickname(reportedMember != null ? reportedMember.getNickname() : null)
        .reportedMemberAccountStatus(reportedMember != null && reportedMember.getAccountStatus() != null
            ? reportedMember.getAccountStatus().name() : null)
        .reportedMemberSuspended(reportedMember != null
            && reportedMember.getAccountStatus() == com.romrom.common.constant.AccountStatus.SUSPENDED_ACCOUNT)
        .reportedMemberTotalReportCount(countTotalReportsAgainst(reportedMember))
        .build();
  }

  /**
   * 동일 피신고자에 대한 누적 신고 건수 (물품 신고 + 회원 신고 합산)
   */
  private Long countTotalReportsAgainst(Member reportedMember) {
    if (reportedMember == null) {
      return 0L;
    }
    return itemReportRepository.countByItemMember(reportedMember)
        + memberReportRepository.countByTargetMember(reportedMember);
  }

  @Transactional
  public AdminResponse updateStatus(AdminRequest request) {
    if (ReportType.ITEM == request.getReportType()) {
      ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      itemReport.setStatus(request.getNewReportStatus());
      itemReportRepository.save(itemReport);
    } else if (ReportType.MEMBER == request.getReportType()) {
      MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      memberReport.setStatus(request.getNewReportStatus());
      memberReportRepository.save(memberReport);
    } else {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    return AdminResponse.builder().build();
  }

  /**
   * 신고 원스톱 처리 (#709)
   * - 한 트랜잭션에서 (a) 후속 액션(정지/물품삭제/반려) 실행 + (b) 신고 상태 변경
   * - 정지/물품삭제 시 SanctionHistory에 reportId가 연결되도록 기존 suspendMember 로직 재사용
   *
   * @param request reportId, reportType(ITEM/MEMBER), resolveAction, (정지 시) suspendReason/suspendedUntil,
   *                (물품삭제 시) itemAdminDeleteDetail
   */
  @Transactional
  public AdminResponse resolveReport(AdminRequest request) {
    if (request.getReportId() == null || request.getReportType() == null || request.getResolveAction() == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    log.debug("신고 원스톱 처리: reportId={}, reportType={}, action={}",
        request.getReportId(), request.getReportType(), request.getResolveAction());

    ResolveAction action = request.getResolveAction();

    if (ReportType.ITEM == request.getReportType()) {
      ItemReport itemReport = itemReportRepository.findByItemReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      resolveItemReport(itemReport, action, request);
    } else if (ReportType.MEMBER == request.getReportType()) {
      MemberReport memberReport = memberReportRepository.findByMemberReportId(request.getReportId())
          .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
      resolveMemberReport(memberReport, action, request);
    } else {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }

    return AdminResponse.builder().build();
  }

  /**
   * 물품 신고 처리 분기
   * - SUSPEND_MEMBER: 물품 소유자 정지 → COMPLETED
   * - DELETE_ITEM: 물품 삭제 → COMPLETED
   * - REJECT: 액션 없이 REJECTED
   */
  private void resolveItemReport(ItemReport itemReport, ResolveAction action, AdminRequest request) {
    Item reportedItem = itemReport.getItem();
    Member reportedMember = (reportedItem != null) ? reportedItem.getMember() : null;

    switch (action) {
      case SUSPEND_MEMBER -> {
        if (reportedMember == null) {
          throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        suspendReportedMember(reportedMember.getMemberId(), request);
        itemReport.setStatus(ReportStatus.COMPLETED);
      }
      case DELETE_ITEM -> {
        if (reportedItem == null) {
          throw new CustomException(ErrorCode.ITEM_NOT_FOUND);
        }
        // 사유 분류는 신고 기반 ETC, 상세는 관리자 입력(itemAdminDeleteDetail)
        itemService.deleteItemByAdmin(reportedItem.getItemId(),
            ItemAdminDeleteReason.ETC, request.getItemAdminDeleteDetail());
        itemReport.setStatus(ReportStatus.COMPLETED);
      }
      case REJECT -> itemReport.setStatus(ReportStatus.REJECTED);
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    itemReportRepository.save(itemReport);
  }

  /**
   * 회원 신고 처리 분기
   * - SUSPEND_MEMBER: 피신고자 정지 → COMPLETED
   * - DELETE_ITEM: 회원 신고에는 물품이 없으므로 잘못된 요청
   * - REJECT: 액션 없이 REJECTED
   */
  private void resolveMemberReport(MemberReport memberReport, ResolveAction action, AdminRequest request) {
    Member reportedMember = memberReport.getTargetMember();

    switch (action) {
      case SUSPEND_MEMBER -> {
        if (reportedMember == null) {
          throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        suspendReportedMember(reportedMember.getMemberId(), request);
        memberReport.setStatus(ReportStatus.COMPLETED);
      }
      case REJECT -> memberReport.setStatus(ReportStatus.REJECTED);
      // 회원 신고에 DELETE_ITEM 액션은 대상 물품이 없어 잘못된 요청
      case DELETE_ITEM -> throw new CustomException(ErrorCode.INVALID_REQUEST);
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    memberReportRepository.save(memberReport);
  }

  /**
   * 피신고자 정지 — 기존 suspendMember 재사용.
   * reportId/reportType을 함께 넘겨 SanctionHistory에 신고 연결이 기록되도록 한다.
   */
  private void suspendReportedMember(java.util.UUID reportedMemberId, AdminRequest request) {
    AdminRequest suspendRequest = AdminRequest.builder()
        .memberId(reportedMemberId)
        .suspendReason(request.getSuspendReason())
        .suspendedUntil(request.getSuspendedUntil())
        .reportId(request.getReportId())
        .reportType(request.getReportType())
        .build();
    adminMemberService.suspendMember(suspendRequest);
  }

  /**
   * 신고접수(PENDING) 건수 조회 (관리자 대시보드용)
   * - 물품 신고 + 회원 신고 PENDING 합산
   */
  @Transactional(readOnly = true)
  public long countPendingReports() {
    return itemReportRepository.countByStatus(ReportStatus.PENDING)
        + memberReportRepository.countByStatus(ReportStatus.PENDING);
  }

  @Transactional(readOnly = true)
  public AdminResponse getStats() {
    Map<String, Long> itemStats = new LinkedHashMap<>();
    Map<String, Long> memberStats = new LinkedHashMap<>();

    for (ReportStatus status : ReportStatus.values()) {
      itemStats.put(status.name(), itemReportRepository.countByStatus(status));
      memberStats.put(status.name(), memberReportRepository.countByStatus(status));
    }

    Map<String, Map<String, Long>> stats = new LinkedHashMap<>();
    stats.put("item", itemStats);
    stats.put("member", memberStats);

    return AdminResponse.builder()
        .reportStats(stats)
        .build();
  }
}
