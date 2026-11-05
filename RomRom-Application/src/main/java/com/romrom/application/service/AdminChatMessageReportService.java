package com.romrom.application.service;

import com.romrom.application.dto.AdminChatMessageReportResponse;
import com.romrom.application.dto.AdminRequest;
import com.romrom.application.dto.AdminResponse;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
import com.romrom.report.repository.ChatMessageReportRepository;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 채팅 메시지 신고 (#873): 목록·상세, 상태 변경, 원스톱 처리(정지·반려), 상태별 건수
 * 상태 변경·처리 진입점은 AdminReportService (reportType=CHAT_MESSAGE 위임)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminChatMessageReportService {

  private final ChatMessageReportRepository chatMessageReportRepository;
  private final ItemReportRepository itemReportRepository;
  private final MemberReportRepository memberReportRepository;
  private final AdminMemberSanctionService adminMemberSanctionService;

  @Transactional(readOnly = true)
  public AdminChatMessageReportResponse getChatMessageReports(AdminRequest request) {
    PageRequest pageRequest = PageRequest.of(request.getPageNumber(), request.getPageSize());
    Page<ChatMessageReport> chatMessageReportPage = request.getReportStatus() != null
        ? chatMessageReportRepository.findByStatusOrderByCreatedDateDesc(request.getReportStatus(), pageRequest)
        : chatMessageReportRepository.findAllByOrderByCreatedDateDesc(pageRequest);

    return AdminChatMessageReportResponse.builder()
        .chatMessageReports(chatMessageReportPage.getContent())
        .totalPages(chatMessageReportPage.getTotalPages())
        .totalElements(chatMessageReportPage.getTotalElements())
        .currentPage(chatMessageReportPage.getNumber())
        .build();
  }

  @Transactional(readOnly = true)
  public AdminChatMessageReportResponse getChatMessageReportDetail(AdminRequest request) {
    ChatMessageReport chatMessageReport = findChatMessageReport(request.getReportId());
    return AdminChatMessageReportResponse.builder()
        .chatMessageReport(chatMessageReport)
        .reportResolveDetail(buildResolveDetail(chatMessageReport))
        .build();
  }

  @Transactional
  public void updateStatus(UUID chatMessageReportId, ReportStatus newReportStatus) {
    ChatMessageReport chatMessageReport = findChatMessageReport(chatMessageReportId);
    chatMessageReport.setStatus(newReportStatus);
    chatMessageReportRepository.save(chatMessageReport);
  }

  /**
   * 채팅 신고 처리 분기
   * - SUSPEND_MEMBER: 대상 회원 정지 (제재 이력에 신고 연결) > COMPLETED
   * - REJECT: 액션 없이 REJECTED
   * - DELETE_ITEM: 대상 물품이 없어 잘못된 요청
   */
  @Transactional
  public void resolve(AdminRequest request) {
    ChatMessageReport chatMessageReport = findChatMessageReport(request.getReportId());
    switch (request.getResolveAction()) {
      case SUSPEND_MEMBER -> {
        Member reportedMember = chatMessageReport.getTargetMember();
        if (reportedMember == null) {
          throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        suspendReportedMember(reportedMember.getMemberId(), request);
        chatMessageReport.setStatus(ReportStatus.COMPLETED);
      }
      case REJECT -> chatMessageReport.setStatus(ReportStatus.REJECTED);
      default -> throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    chatMessageReportRepository.save(chatMessageReport);
    log.info("[admin chat message report] resolved : reportId={}, action={}",
        chatMessageReport.getChatMessageReportId(), request.getResolveAction());
  }

  @Transactional(readOnly = true)
  public long countByStatus(ReportStatus reportStatus) {
    return chatMessageReportRepository.countByStatus(reportStatus);
  }

  private ChatMessageReport findChatMessageReport(UUID chatMessageReportId) {
    if (chatMessageReportId == null) {
      throw new CustomException(ErrorCode.INVALID_REQUEST);
    }
    return chatMessageReportRepository.findByChatMessageReportId(chatMessageReportId)
        .orElseThrow(() -> new CustomException(ErrorCode.REPORT_NOT_FOUND));
  }

  // 처리용 요약: 대상 회원 상태 + 누적 신고 (물품 + 회원 + 채팅 신고 합산)
  private AdminResponse.ReportResolveDetail buildResolveDetail(ChatMessageReport chatMessageReport) {
    Member reportedMember = chatMessageReport.getTargetMember();
    if (reportedMember == null) {
      return AdminResponse.ReportResolveDetail.builder()
          .reportType(ReportType.CHAT_MESSAGE.name())
          .reportedMemberTotalReportCount(0L)
          .build();
    }
    long reportedMemberTotalReportCount = itemReportRepository.countByItemMember(reportedMember)
        + memberReportRepository.countByTargetMember(reportedMember)
        + chatMessageReportRepository.countByTargetMember(reportedMember);
    return AdminResponse.ReportResolveDetail.builder()
        .reportType(ReportType.CHAT_MESSAGE.name())
        .reportedMemberId(reportedMember.getMemberId())
        .reportedMemberNickname(reportedMember.getNickname())
        .reportedMemberAccountStatus(reportedMember.getAccountStatus() != null
            ? reportedMember.getAccountStatus().name() : null)
        .reportedMemberSuspended(reportedMember.getAccountStatus() == AccountStatus.SUSPENDED_ACCOUNT)
        .reportedMemberTotalReportCount(reportedMemberTotalReportCount)
        .build();
  }

  // 대상 회원 정지: 신고 ID·유형을 넘겨 제재 이력에 신고 연결
  private void suspendReportedMember(UUID reportedMemberId, AdminRequest request) {
    adminMemberSanctionService.suspendMember(AdminRequest.builder()
        .memberId(reportedMemberId)
        .suspendReason(request.getSuspendReason())
        .suspendedUntil(request.getSuspendedUntil())
        .reportId(request.getReportId())
        .reportType(ReportType.CHAT_MESSAGE)
        .executorAdminId(request.getExecutorAdminId())
        .build());
  }
}
