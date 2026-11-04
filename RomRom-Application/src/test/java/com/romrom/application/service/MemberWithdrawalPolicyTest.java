package com.romrom.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.auth.jwt.JwtUtil;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.constant.MemberWithdrawalReason;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.service.ItemService;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberWithdrawalHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.MemberWithdrawalHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 회원 탈퇴 정책: 진행 중 거래 취소 + 탈퇴 기록 (사유 선택, 구버전 앱 사유 없음 허용), 상세 사유 길이 검증 (#862)
 */
@ExtendWith(MockitoExtension.class)
class MemberWithdrawalPolicyTest {

  @Mock private MemberService memberService;
  @Mock private MemberRepository memberRepository;
  @Mock private SanctionHistoryRepository sanctionHistoryRepository;
  @Mock private ItemService itemService;
  @Mock private ChatRoomService chatRoomService;
  @Mock private JwtUtil jwtUtil;
  @Mock private TradeRequestHistoryRepository tradeRequestHistoryRepository;
  @Mock private MemberWithdrawalHistoryRepository memberWithdrawalHistoryRepository;
  @Mock private HttpServletRequest httpServletRequest;
  @InjectMocks private MemberApplicationService memberApplicationService;

  private final UUID memberId = UUID.randomUUID();

  @Test
  @DisplayName("사유 전송 > 진행 중 거래 취소 수와 사유·상세 사유(공백 제거) 기록 후 탈퇴 진행")
  void recordsReasonAndCanceledTradeCount() {
    when(tradeRequestHistoryRepository.cancelAllOngoingByMemberId(memberId)).thenReturn(2);
    MemberRequest request = buildRequest(MemberWithdrawalReason.NO_WANTED_ITEMS, "  원하는 물건이 없어요  ");

    memberApplicationService.deleteMember(request, httpServletRequest);

    MemberWithdrawalHistory savedHistory = captureSavedHistory();
    assertEquals(MemberWithdrawalReason.NO_WANTED_ITEMS, savedHistory.getWithdrawalReason());
    assertEquals("원하는 물건이 없어요", savedHistory.getWithdrawalReasonDetail());
    assertEquals(2, savedHistory.getCanceledTradeRequestCount());
    verify(memberRepository).deleteByMemberId(memberId);
  }

  @Test
  @DisplayName("구버전 앱 (사유 없음) > 사유 null 기록, 탈퇴 정상 진행")
  void allowsWithdrawalWithoutReason() {
    memberApplicationService.deleteMember(buildRequest(null, null), httpServletRequest);

    MemberWithdrawalHistory savedHistory = captureSavedHistory();
    assertNull(savedHistory.getWithdrawalReason());
    assertNull(savedHistory.getWithdrawalReasonDetail());
    verify(memberRepository).deleteByMemberId(memberId);
  }

  @Test
  @DisplayName("상세 사유 500자 초과 > TOO_LONG_WITHDRAWAL_REASON_DETAIL, 거래 취소·탈퇴 안 함")
  void rejectsTooLongReasonDetail() {
    MemberRequest request = buildRequest(MemberWithdrawalReason.OTHER, "가".repeat(501));

    CustomException exception = assertThrows(CustomException.class,
        () -> memberApplicationService.deleteMember(request, httpServletRequest));

    assertEquals(ErrorCode.TOO_LONG_WITHDRAWAL_REASON_DETAIL, exception.getErrorCode());
    verify(tradeRequestHistoryRepository, never()).cancelAllOngoingByMemberId(any());
    verify(memberRepository, never()).deleteByMemberId(any());
  }

  private MemberRequest buildRequest(MemberWithdrawalReason withdrawalReason, String withdrawalReasonDetail) {
    return MemberRequest.builder()
        .member(Member.builder().memberId(memberId).build())
        .withdrawalReason(withdrawalReason)
        .withdrawalReasonDetail(withdrawalReasonDetail)
        .build();
  }

  private MemberWithdrawalHistory captureSavedHistory() {
    ArgumentCaptor<MemberWithdrawalHistory> historyCaptor = ArgumentCaptor.forClass(MemberWithdrawalHistory.class);
    verify(memberWithdrawalHistoryRepository).save(historyCaptor.capture());
    return historyCaptor.getValue();
  }
}
