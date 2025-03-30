package com.romrom.romback.domain.service;

import static com.romrom.romback.global.util.LogUtil.lineLogDebug;
import static com.romrom.romback.global.util.LogUtil.superLogDebug;

import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.dto.MemberResponse;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import com.romrom.romback.domain.repository.postgres.MemberLocationRepository;
import com.romrom.romback.domain.repository.postgres.MemberItemCategoryRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
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

  private final MemberLocationRepository memberLocationRepository;
  private final MemberItemCategoryRepository memberItemCategoryRepository;

  public MemberResponse getMemberInfo(MemberRequest request) {
    return MemberResponse.builder()
        .member(request.getMember())
        .memberLocation(memberLocationRepository.findByMemberMemberId(request.getMember().getMemberId())
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_LOCATION_NOT_FOUND)))
        .memberItemCategories(memberItemCategoryRepository.findByMemberMemberId(request.getMember().getMemberId()))
        .build();
  }

  /**
   * 회원 선호 카테고리 리스트 저장
   */
  @Transactional
  public void saveMemberProductCategories(MemberRequest request) {
    // 회원 정보 추출
    Member member = request.getMember();

    // 기존 선호 카테고리 삭제
    memberItemCategoryRepository.deleteByMember(member);

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

    List<MemberItemCategory> memberProductCategories = memberItemCategoryRepository.saveAll(preferences);

    //FIXME: 임시 로깅 출력
    lineLogDebug("저장된 회원 선호 카테고리 리스트 : " + member.getEmail());
    superLogDebug(memberProductCategories);
    lineLogDebug(null);
    return;
  }
}