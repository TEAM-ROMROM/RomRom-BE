package com.romrom.report.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.report.dto.ReportRequest;
import com.romrom.report.entity.ChatMessageReport;
import com.romrom.report.event.ReportAlertEvent;
import com.romrom.report.repository.ChatMessageReportRepository;
import com.romrom.report.repository.ItemReportRepository;
import com.romrom.report.repository.MemberReportRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 채팅 메시지 신고 저장: 자기 신고·중복·기타 의견 누락 거부, 정상 저장 + 관리자 알림
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageReportCreateTest {

  @Mock private ItemReportRepository itemReportRepository;
  @Mock private MemberReportRepository memberReportRepository;
  @Mock private ChatMessageReportRepository chatMessageReportRepository;
  @Mock private ItemRepository itemRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks
  private ReportService reportService;

  private final Member reporter = Member.builder().memberId(UUID.randomUUID()).build();
  private final Member targetMember = Member.builder().memberId(UUID.randomUUID()).nickname("상대").build();

  private ReportRequest createRequest(Set<Integer> reasonCodes, String extraComment) {
    return ReportRequest.builder().member(reporter).chatRoomId(UUID.randomUUID()).chatMessageId("message-1")
        .memberReportReasons(reasonCodes).extraComment(extraComment).build();
  }

  private ErrorCode errorOf(ReportRequest request, UUID targetMemberId) {
    return Assertions.assertThrows(CustomException.class,
        () -> reportService.createChatMessageReport(request, targetMemberId, "본문")).getErrorCode();
  }

  @Test
  @DisplayName("내 메시지 신고 > SELF_REPORT, 기타 사유 의견 누락 > NULL_EXTRA_COMMENT, 모르는 사유 > INVALID_REQUEST")
  void 신고_거부() {
    Assertions.assertEquals(ErrorCode.SELF_REPORT, errorOf(createRequest(Set.of(1), null), reporter.getMemberId()));
    Assertions.assertEquals(ErrorCode.NULL_EXTRA_COMMENT, errorOf(createRequest(Set.of(5), " "), targetMember.getMemberId()));

    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));
    Assertions.assertEquals(ErrorCode.INVALID_REQUEST, errorOf(createRequest(Set.of(99), null), targetMember.getMemberId()));
  }

  @Test
  @DisplayName("같은 메시지 중복 신고 > DUPLICATE_CHAT_MESSAGE_REPORT")
  void 중복_신고_거부() {
    when(chatMessageReportRepository.existsByChatMessageIdAndReporterMemberId("message-1", reporter.getMemberId()))
        .thenReturn(true);

    Assertions.assertEquals(ErrorCode.DUPLICATE_CHAT_MESSAGE_REPORT,
        errorOf(createRequest(Set.of(1), null), targetMember.getMemberId()));
    verify(chatMessageReportRepository, never()).save(any());
  }

  @Test
  @DisplayName("정상 신고 > 저장 + 관리자 알림 이벤트")
  void 신고_저장() {
    when(memberRepository.findById(targetMember.getMemberId())).thenReturn(Optional.of(targetMember));

    reportService.createChatMessageReport(createRequest(Set.of(1, 5), "욕설"), targetMember.getMemberId(), "본문");

    verify(chatMessageReportRepository).save(any(ChatMessageReport.class));
    verify(applicationEventPublisher).publishEvent(any(ReportAlertEvent.class));
  }
}
