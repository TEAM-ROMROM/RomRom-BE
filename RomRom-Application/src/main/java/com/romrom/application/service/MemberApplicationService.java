package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;

import com.romrom.auth.jwt.JwtUtil;
import com.romrom.chat.service.ChatRoomService;
import com.romrom.item.service.ItemService;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ItemService itemService;
    private final ChatRoomService chatRoomService;
    private final JwtUtil jwtUtil;

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

        // 1. Member 도메인 관련 데이터 삭제
        memberService.deleteMemberRelatedData(request);

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