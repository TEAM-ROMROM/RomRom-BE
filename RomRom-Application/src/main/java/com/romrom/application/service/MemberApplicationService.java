package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;

import com.romrom.auth.dto.AuthRequest;
import com.romrom.auth.dto.AuthResponse;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.mongo.ItemCustomTagsRepository;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemCustomTagsRepository itemCustomTagsRepository;
    private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
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

        // 1. Member 도메인 관련 데이터 삭제
        memberService.deleteMemberRelatedData(request);

        // 2. Item 도메인 관련 데이터 삭제
        List<Item> items = itemRepository.findByMemberMemberId(member.getMemberId());
        items.forEach(item -> {
            tradeRequestHistoryRepository.deleteAllByGiveItemItemId(item.getItemId());
            tradeRequestHistoryRepository.deleteAllByTakeItemItemId(item.getItemId());
            itemImageRepository.deleteByItemItemId(item.getItemId());
            itemCustomTagsRepository.deleteByItemId(item.getItemId());
        });
        itemRepository.deleteByMemberMemberId(member.getMemberId());

        // 3. Auth 관련 토큰 비활성화
        String key = REFRESH_KEY_PREFIX + member.getMemberId();
        String accessToken = jwtUtil.extractAccessToken(httpServletRequest);
        jwtUtil.deactivateToken(accessToken, key);

        // 4. 회원 삭제
        memberRepository.deleteByMemberId(member.getMemberId());
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