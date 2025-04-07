package com.romrom.romback.domain.service;

import static com.romrom.romback.global.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static com.romrom.romback.global.util.LogUtil.lineLogDebug;
import static com.romrom.romback.global.util.LogUtil.superLogDebug;

import com.romrom.romback.domain.object.constant.ItemCategory;
import com.romrom.romback.domain.object.dto.MemberRequest;
import com.romrom.romback.domain.object.dto.MemberResponse;
import com.romrom.romback.domain.object.postgres.Item;
import com.romrom.romback.domain.object.postgres.Member;
import com.romrom.romback.domain.object.postgres.MemberItemCategory;
import com.romrom.romback.domain.object.postgres.MemberLocation;
import com.romrom.romback.domain.repository.mongo.ItemCustomTagsRepository;
import com.romrom.romback.domain.repository.postgres.ItemImageRepository;
import com.romrom.romback.domain.repository.postgres.ItemRepository;
import com.romrom.romback.domain.repository.postgres.MemberItemCategoryRepository;
import com.romrom.romback.domain.repository.postgres.MemberLocationRepository;
import com.romrom.romback.domain.repository.postgres.MemberRepository;
import com.romrom.romback.domain.repository.postgres.TradeRequestHistoryRepository;
import com.romrom.romback.global.exception.CustomException;
import com.romrom.romback.global.exception.ErrorCode;
import com.romrom.romback.global.jwt.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
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
  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final ItemCustomTagsRepository itemCustomTagsRepository;
  private final TradeRequestHistoryRepository tradeRequestHistoryRepository;
  private final JwtUtil jwtUtil;

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

    List<MemberItemCategory> memberProductCategories = memberItemCategoryRepository.saveAll(preferences);

    //FIXME: 임시 로깅 출력
    lineLogDebug("저장된 회원 선호 카테고리 리스트 : " + member.getEmail());
    superLogDebug(memberProductCategories);
    lineLogDebug(null);
    return;
  }

  /**
   * 회원 삭제 (Soft Delete)
   * 회원 탈퇴를 진행합니다
   * 회원 정보 및 회원이 등록한 물품은 softDelete 처리하며, 그 외 데이터는 모두 hardDelete 처리합니다
   *
   * SoftDelete
   * 1. 회원 정보
   * 2. 회원 등록 물품
   *
   * @param request member
   */
  @Transactional
  public void deleteMember(MemberRequest request, HttpServletRequest httpServletRequest) {
    // 1. 회원 정보 추출
    Member member = request.getMember();

    // 2. 회원 위치정보 삭제 (hardDelete)
    memberLocationRepository.deleteByMemberMemberId(member.getMemberId());

    // 3. 회원 선호 카테고리 삭제 (hardDelete)
    memberItemCategoryRepository.deleteByMemberMemberId(member.getMemberId());

    // 4. 회원이 등록한 ItemImage & CustomTags & TradeHistory 삭제 (hardDelete)
    List<Item> items = itemRepository.findByMemberMemberId(member.getMemberId());
    items.forEach(item -> {
      tradeRequestHistoryRepository.deleteAllByGiveItem_ItemId(item.getItemId());
      tradeRequestHistoryRepository.deleteAllByTakeItem_ItemId(item.getItemId());
      itemImageRepository.deleteByItemItemId(item.getItemId());
      itemCustomTagsRepository.deleteByItemId(item.getItemId());
    });
    // 5. 회원이 등록한 물품 삭제
    itemRepository.deleteByMemberMemberId(member.getMemberId());

    // 6. 토큰 비활성화
    String key = REFRESH_KEY_PREFIX + member.getMemberId();
    String accessToken = jwtUtil.extractAccessToken(httpServletRequest);
    jwtUtil.deactivateToken(accessToken, key);

    // 7. 회원 삭제
    memberRepository.deleteByMemberId(member.getMemberId());
  }
}