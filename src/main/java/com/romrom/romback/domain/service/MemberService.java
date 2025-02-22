package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.LogUtil.lineLog;
import static com.romrom.romback.global.util.LogUtil.superLog;

import com.romrom.romback.domain.object.constant.ProductCategory;
import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberProductCategory;
import com.romrom.romback.domain.repository.postgres.MemberProductCategoryRepository;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

  private final MemberRepository memberRepository;
  private final MemberProductCategoryRepository memberProductCategoryRepository;

  /**
   * 회원 선호 카테고리 리스트 저장
   */
  @Transactional
  public void saveMemberProductCategories(MemberRequest request) {
    // 회원 정보 추출
    Member member = memberRepository.findById(request.getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    UUID memberId = member.getMemberId();

    // 기존 선호 카테고리 삭제
    memberProductCategoryRepository.deleteByMemberMemberId(memberId);

    // 새로운 선호 카테고리 생성 및 저장
    List<MemberProductCategory> preferences = new ArrayList<>();
    for (Integer code : request.getMemberProductCategories()) {
      ProductCategory productCategory = ProductCategory.fromCode(code);
      MemberProductCategory preference = MemberProductCategory.builder()
          .member(member)
          .productCategory(productCategory)
          .build();
      preferences.add(preference);
    }

    List<MemberProductCategory> memberProductCategories = memberProductCategoryRepository.saveAll(preferences);

    // 로깅 출력
    lineLog("저장된 회원 선호 카테고리 리스트 : " + memberId.toString());
    superLog(memberProductCategories);
    lineLog(null);

    return;
  }
}