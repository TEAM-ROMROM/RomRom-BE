package com.romrom.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.application.dto.AdminRequest;
import com.romrom.common.constant.ResolveAction;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.entity.Member;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.enums.ReportStatus;
import com.romrom.report.enums.ReportType;
import com.romrom.report.repository.ChatMessageReportRepository;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 관리자 채팅 메시지 신고 처리 단위 테스트 (#873): 정지 > 신고 연결·완료, 반려, 물품 삭제 거부, 없는 신고
 */
@ExtendWith(MockitoExtension.class)
class AdminChatMessageReportServiceTest {

  private static final UUID REPORT_ID = UUID.randomUUID();

  @Mock
  private ChatMessageReportRepository chatMessageReportRepository;

  @Mock
  private ItemReportRepository itemReportRepository;

  @Mock
  private MemberReportRepository memberReportRepository;

  @Mock
  private AdminMemberSanctionService adminMemberSanctionService;

  @InjectMocks
  private AdminChatMessageReportService adminChatMessageReportService;

  private ChatMessageReport givenPendingChatMessageReport(Member targetMember) {
    ChatMessageReport chatMessageReport = ChatMessageReport.builder()
        .chatMessageReportId(REPORT_ID)
        .targetMember(targetMember)
        .status(ReportStatus.PENDING)
        .build();
    when(chatMessageReportRepository.findByChatMessageReportId(REPORT_ID)).thenReturn(Optional.of(chatMessageReport));
    return chatMessageReport;
  }

  private static AdminRequest createResolveRequest(ResolveAction resolveAction) {
    return AdminRequest.builder()
        .reportId(REPORT_ID)
        .reportType(ReportType.CHAT_MESSAGE)
        .resolveAction(resolveAction)
        .suspendReason("욕설")
        .build();
  }

  @Test
  @DisplayName("SUSPEND_MEMBER > 메시지 보낸 회원 정지(신고 ID·CHAT_MESSAGE 연결) + COMPLETED")
  void resolve_정지_신고완료() {
    Member targetMember = Member.builder().memberId(UUID.randomUUID()).build();
    ChatMessageReport chatMessageReport = givenPendingChatMessageReport(targetMember);

    adminChatMessageReportService.resolve(createResolveRequest(ResolveAction.SUSPEND_MEMBER));

    ArgumentCaptor<AdminRequest> suspendRequestCaptor = ArgumentCaptor.forClass(AdminRequest.class);
    verify(adminMemberSanctionService).suspendMember(suspendRequestCaptor.capture());
    Assertions.assertEquals(targetMember.getMemberId(), suspendRequestCaptor.getValue().getMemberId());
    Assertions.assertEquals(REPORT_ID, suspendRequestCaptor.getValue().getReportId());
    Assertions.assertEquals(ReportType.CHAT_MESSAGE, suspendRequestCaptor.getValue().getReportType());
    Assertions.assertEquals(ReportStatus.COMPLETED, chatMessageReport.getStatus());
  }

  @Test
  @DisplayName("REJECT > 정지 없이 REJECTED")
  void resolve_반려() {
    ChatMessageReport chatMessageReport = givenPendingChatMessageReport(Member.builder().memberId(UUID.randomUUID()).build());

    adminChatMessageReportService.resolve(createResolveRequest(ResolveAction.REJECT));

    verify(adminMemberSanctionService, never()).suspendMember(any());
    Assertions.assertEquals(ReportStatus.REJECTED, chatMessageReport.getStatus());
  }

  @Test
  @DisplayName("DELETE_ITEM > 대상 물품 없음 INVALID_REQUEST, 상태 유지")
  void resolve_물품삭제_거부() {
    ChatMessageReport chatMessageReport = givenPendingChatMessageReport(Member.builder().memberId(UUID.randomUUID()).build());

    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> adminChatMessageReportService.resolve(createResolveRequest(ResolveAction.DELETE_ITEM)));

    Assertions.assertEquals(ErrorCode.INVALID_REQUEST, customException.getErrorCode());
    Assertions.assertEquals(ReportStatus.PENDING, chatMessageReport.getStatus());
  }

  @Test
  @DisplayName("없는 신고 상태 변경 > REPORT_NOT_FOUND")
  void updateStatus_없는신고() {
    when(chatMessageReportRepository.findByChatMessageReportId(REPORT_ID)).thenReturn(Optional.empty());

    CustomException customException = Assertions.assertThrows(CustomException.class,
        () -> adminChatMessageReportService.updateStatus(REPORT_ID, ReportStatus.COMPLETED));

    Assertions.assertEquals(ErrorCode.REPORT_NOT_FOUND, customException.getErrorCode());
  }
}
