package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;

import com.romrom.auth.jwt.JwtUtil;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.TradeResponse;
import com.romrom.item.entity.postgres.TradeRequestHistory;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.item.service.ItemService;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberWithdrawalHistory;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.repository.MemberWithdrawalHistoryRepository;
import com.romrom.member.repository.mongo.SanctionHistoryRepository;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberApplicationService {

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final SanctionHistoryRepository sanctionHistoryRepository;
    private final ItemService itemService;
    private final ChatRoomService chatRoomService;
    private final JwtUtil jwtUtil;
    private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
    private final MemberWithdrawalHistoryRepository memberWithdrawalHistoryRepository;

    // 탈퇴 상세 사유 최대 길이 (member_withdrawal_history.withdrawal_reason_detail 컬럼 길이)
    private static final int MAXIMUM_WITHDRAWAL_REASON_DETAIL_LENGTH = 500;

    /**
     * 회원 삭제 (크로스 도메인 로직)
     * 회원 탈퇴를 진행합니다
     * 회원 정보 및 회원이 등록한 물품은 softDelete 처리하며, 그 외 데이터는 모두 hardDelete 처리합니다
     *
     * @param request member
     */
    @Transactional
    public void deleteMember(MemberRequest request, HttpServletRequest httpServletRequest) {
        Member member = request.getMember();
        UUID memberId = member.getMemberId();
        String withdrawalReasonDetail = normalizeWithdrawalReasonDetail(request.getWithdrawalReasonDetail());

        // 0. 진행 중 거래 CANCELED (대기·채팅 중·교환 완료 요청) + 탈퇴 기록 (사유 미전송 구버전 앱 포함) (#862)
        int canceledTradeRequestCount = tradeRequestHistoryRepository.cancelAllOngoingByMemberId(memberId);
        memberWithdrawalHistoryRepository.save(MemberWithdrawalHistory.builder()
            .memberId(memberId)
            .withdrawalReason(request.getWithdrawalReason())
            .withdrawalReasonDetail(withdrawalReasonDetail)
            .canceledTradeRequestCount(canceledTradeRequestCount)
            .build());
        log.info("[member withdrawal] recorded : memberId={}, reason={}, canceledTradeRequestCount={}",
            memberId, request.getWithdrawalReason(), canceledTradeRequestCount);

        // 1. Member 도메인 관련 데이터 삭제
        memberService.deleteMemberRelatedData(request);

        // 1.5. SanctionHistory 삭제 (MongoDB)
        sanctionHistoryRepository.deleteAllByMemberId(memberId);
        log.debug("제재 이력 삭제 완료: memberId={}", memberId);

        // 2. Chat 도메인 관련 데이터 삭제 (TradeRequestHistory 삭제 전에 먼저 처리)
        chatRoomService.deleteAllChatRoomsByMemberId(memberId);

        // TODO : TradeRequestHistory 도메인 관련 데이터 삭제
        // 3. Item 도메인 관련 모든 데이터 및 모든 Item 삭제
        itemService.deleteAllRelatedItemInfoByMemberId(memberId);

        // 4. Auth 관련 토큰 비활성화
        String key = REFRESH_KEY_PREFIX + memberId;
        String accessToken = jwtUtil.extractAccessToken(httpServletRequest);
        jwtUtil.deactivateToken(accessToken, key);

        // 5. 회원 삭제
        memberRepository.deleteByMemberId(memberId);
    }

    /**
     * 탈퇴 전 안내용 진행 중 거래 목록 (대기·채팅 중·교환 완료 요청, 탈퇴 시 전부 취소 대상) (#862)
     */
    @Transactional(readOnly = true)
    public TradeResponse getOngoingTradesBeforeWithdrawal(Member member) {
        List<TradeRequestHistory> ongoingTradeRequestHistories =
            tradeRequestHistoryRepository.findAllOngoingByMemberIdWithItems(member.getMemberId());
        return TradeResponse.builder()
            .ongoingTradeRequestHistories(ongoingTradeRequestHistories)
            .build();
    }

    /**
     * 탈퇴 상세 사유 정리: 앞뒤 공백 제거 (빈 값 > null) > 최대 길이 초과 거부
     */
    private String normalizeWithdrawalReasonDetail(String withdrawalReasonDetail) {
        if (withdrawalReasonDetail == null || withdrawalReasonDetail.isBlank()) {
            return null;
        }
        String trimmedReasonDetail = withdrawalReasonDetail.trim();
        if (trimmedReasonDetail.length() > MAXIMUM_WITHDRAWAL_REASON_DETAIL_LENGTH) {
            log.warn("[member withdrawal] rejected 400 : reason detail length={}", trimmedReasonDetail.length());
            throw new CustomException(ErrorCode.TOO_LONG_WITHDRAWAL_REASON_DETAIL);
        }
        return trimmedReasonDetail;
    }

    /**
     * 이용약관 동의 (Application 레벨에서 처리)
     *
     * @param request 회원 요청
     * @return 회원 응답
     */
    @Transactional
    public MemberResponse saveTermsAgreement(MemberRequest request) {
        return memberService.saveTermsAgreement(request);
    }
} 