package com.romrom.romback.domain.service;

import static com.romrom.romback.global.jwt.JwtUtil.*;
import static com.romrom.romback.global.util.LogUtil.lineLogDebug;
import static com.romrom.romback.global.util.LogUtil.superLogDebug;

import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.dto.MemberResponse;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import com.romrom.romback.domain.object.postgres.MemberLocation;
import com.romrom.romback.domain.repository.postgres.MemberItemCategoryRepository;
import com.romrom.romback.domain.repository.postgres.MemberLocationRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.jwt.JwtUtil;
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

  public MemberResponse getMemberInfo(MemberRequest request) {
    MemberLocation memberLocation = memberLocationRepository.findByMemberMemberId(request.getMember().getMemberId())
        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_LOCATION_NOT_FOUND));
    List<MemberItemCategory> memberItemCategories = memberItemCategoryRepository.findByMemberMemberId(request.getMember().getMemberId());

    return MemberResponse.builder()
        .member(request.getMember())
        .memberLocation(memberLocation)
        .memberItemCategories(memberItemCategories)
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

  /**
   * 회원 삭제 (Soft Delete)
   * 회원 탈퇴를 진행하며 회원과 연관되어있는 데이터 모두 SoftDelete 처리합니다
   *
   * @param request member, accessToken
   */
  @Transactional
  public void deleteMember(MemberRequest request) {
    // 1. 회원 정보 추출
    Member member = request.getMember();

    // 2. 회원 위치정보 삭제
    memberLocationRepository.deleteByMemberMemberId(member.getMemberId());

    // 3. 회원 선호 카테고리 삭제
    memberProductCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 4. 회원이 작성한 Item & ItemImage 삭제
    itemImageRepository.deleteByMemberMemberId(member.getMemberId()); // 성능 개선을 위한 벌크 작업
    itemRepository.deleteByMemberMemberId(member.getMemberId());

    // 5. 토큰 비활성화
    String key = REFRESH_KEY_PREFIX + member.getMemberId();
    jwtUtil.deactivateToken(request.getAccessToken(), key);

    // 5. 회원 삭제
    memberRepository.delete(member);
  }
}