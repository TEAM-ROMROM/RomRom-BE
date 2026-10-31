package com.romrom.item.service;

import com.romrom.common.constant.ItemCategory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberItemCategoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 피드 롬업 섞기 (#819): 활성 롬업 확인 > 회원 위치·선호 카테고리 > 후보 조회 > PromotedFeedMixer
 * 활성 롬업·위치·선호 카테고리·후보 중 하나라도 없으면 원본 페이지 그대로 반환 (기존 피드 동작 유지)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotedFeedService {

  // 한 번에 가져오는 롬업 후보 최대 수 (페이지 회전용)
  public static final int PROMOTED_FEED_CANDIDATE_LIMIT = 50;

  private final ItemRepository itemRepository;
  private final MemberLocationRepository memberLocationRepository;
  private final MemberItemCategoryRepository memberItemCategoryRepository;

  @Transactional(readOnly = true)
  public Page<Item> mixPromotedItems(Member viewerMember, Page<Item> organicItemPage, double radiusInMeters) {
    if (organicItemPage.isEmpty()) {
      return organicItemPage;
    }
    LocalDateTime now = LocalDateTime.now();
    if (!itemRepository.existsByPromotionExpiresAtAfterAndIsDeletedFalse(now)) {
      return organicItemPage;
    }

    // 피드 필터 재사용 조건 2개: 회원 위치(반경), 선호 카테고리
    Optional<MemberLocation> viewerLocationOptional = memberLocationRepository.findByMemberMemberId(viewerMember.getMemberId());
    if (viewerLocationOptional.isEmpty()) {
      return organicItemPage;
    }
    List<ItemCategory> preferredCategories = memberItemCategoryRepository.findByMemberMemberId(viewerMember.getMemberId())
        .stream()
        .map(MemberItemCategory::getItemCategory)
        .toList();
    if (preferredCategories.isEmpty()) {
      return organicItemPage;
    }

    Point<G2D> viewerGeometry = viewerLocationOptional.get().getGeom();
    List<Item> promotedItemCandidates = itemRepository.findPromotedFeedCandidates(
        viewerMember.getMemberId(),
        viewerGeometry.getPosition().getLon(),
        viewerGeometry.getPosition().getLat(),
        radiusInMeters,
        preferredCategories,
        now,
        PROMOTED_FEED_CANDIDATE_LIMIT
    );
    // getContent()는 호출마다 새 래퍼 > 한 번만 받아 섞기 결과와 동일성 비교
    List<Item> organicItems = organicItemPage.getContent();
    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(
        organicItems, promotedItemCandidates, viewerMember.getMemberId(), organicItemPage.getNumber());
    if (mixedItems == organicItems) {
      return organicItemPage;
    }
    log.debug("[promoted feed] mixed : memberId={}, promotedCount={}",
        viewerMember.getMemberId(), mixedItems.size() - organicItemPage.getNumberOfElements());
    // 전체 개수는 일반 피드 기준 유지 (롬업 카드는 페이지 위에 덧붙는 카드)
    return new PageImpl<>(mixedItems, organicItemPage.getPageable(), organicItemPage.getTotalElements());
  }
}
