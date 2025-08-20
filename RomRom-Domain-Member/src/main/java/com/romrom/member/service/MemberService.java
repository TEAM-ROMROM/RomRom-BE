package com.romrom.member.service;

import com.romrom.ai.service.EmbeddingService;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.member.dto.MemberRequest;
import com.romrom.member.dto.MemberResponse;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberItemCategoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

  private final MemberRepository memberRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberItemCategoryRepository memberItemCategoryRepository;
  private final EmbeddingService embeddingService;

  /**
   * 사용자 정보 반환
   */
  @Transactional(readOnly = true)
  public MemberResponse getMemberInfo(Member member) {
    MemberLocation memberLocation = memberLocationRepository.findByMemberMemberId(member.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_LOCATION_NOT_FOUND));

    List<MemberItemCategory> memberItemCategory = memberItemCategoryRepository.findByMemberMemberId(member.getMemberId());

    return MemberResponse.builder()
        .member(member)
        .memberLocation(memberLocation)
        .memberItemCategories(memberItemCategory)
        .build();
  }

  /**
   * 회원 선호 카테고리 리스트 저장
   */
  @Transactional
  public void saveMemberItemCategories(MemberRequest request) {
    // 회원 정보 추출
    Member member = request.getMember();

    // 기존 선호 카테고리 삭제
    memberItemCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 새로운 선호 카테고리 생성 및 저장
    List<MemberItemCategory> preferences = new ArrayList<>();
    for (int code : request.getPreferredCategories()) {
      ItemCategory itemCategory = ItemCategory.fromCode(code);
      MemberItemCategory preference = MemberItemCategory.builder()
          .member(member)
          .itemCategory(itemCategory)
          .build();
      preferences.add(preference);
    }

    List<MemberItemCategory> memberItemCategories = memberItemCategoryRepository.saveAll(preferences);

    // 회원 선호 카테고리 저장 완료
    member.setIsItemCategorySaved(true);
    memberRepository.save(member);

    // 회원 선호 카테고리 임베딩 생성 및 저장
    embeddingService.generateAndSaveMemberItemCategoryEmbedding(memberItemCategories);

    log.info("회원 선호 카테고리 저장 완료: memberId={}, 카테고리 수={}", member.getMemberId(), memberItemCategories.size());
  }

  /**
   * 회원 관련 데이터 삭제 (Member 도메인 내 데이터만)
   * Application Service에서 호출되는 순수 도메인 로직
   */
  @Transactional
  public void deleteMemberRelatedData(MemberRequest request) {
    Member member = request.getMember();
    
    // 회원 위치정보 삭제 (hardDelete)
    memberLocationRepository.deleteByMemberMemberId(member.getMemberId());

    // 회원 선호 카테고리 삭제 (hardDelete)  
    memberItemCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 회원 이메일, 닉네임 초기화
    member.setEmail(null);
    member.setNickname(null);
    memberRepository.save(member);
  }

  /**
   * 이용약관 동의
   * 마케팅 정보 수신 동의 여부 및 필수 이용약관 동의 여부를 저장합니다
   *
   * @param request accessToken, refreshToken, isMarketingInfoAgreed
   */
  @Transactional
  public MemberResponse saveTermsAgreement(MemberRequest request) {
    Member member = request.getMember();
    member.setIsRequiredTermsAgreed(true);
    member.setIsMarketingInfoAgreed(request.getIsMarketingInfoAgreed());

    return MemberResponse.builder()
        .member(memberRepository.save(member))
        .build();
  }
}