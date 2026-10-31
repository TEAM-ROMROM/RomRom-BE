package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemCategory;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.MemberItemCategory;
import com.romrom.member.entity.MemberLocation;
import com.romrom.member.repository.MemberItemCategoryRepository;
import com.romrom.member.repository.MemberLocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.geolatte.geom.G2D;
import org.geolatte.geom.Point;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 홈 피드 롬업 섞기 서비스 단위 테스트 (#819): 롬업 없으면 기존 피드 결과와 동일
 */
@ExtendWith(MockitoExtension.class)
class PromotedFeedServiceTest {

  private static final double SEARCH_RADIUS_IN_METERS = 10000.0;

  @Mock
  private ItemRepository itemRepository;

  @Mock
  private MemberLocationRepository memberLocationRepository;

  @Mock
  private MemberItemCategoryRepository memberItemCategoryRepository;

  @InjectMocks
  private PromotedFeedService promotedFeedService;

  private Member viewerMember;
  private Page<Item> organicItemPage;

  @BeforeEach
  void setUp() {
    viewerMember = Member.builder().memberId(UUID.randomUUID()).build();
    List<Item> organicItems = IntStream.range(0, 10)
        .mapToObj(index -> createOtherMemberItem())
        .toList();
    organicItemPage = new PageImpl<>(organicItems, PageRequest.of(0, 10), 25);
  }

  private static Item createOtherMemberItem() {
    return Item.builder().itemId(UUID.randomUUID()).member(Member.builder().memberId(UUID.randomUUID()).build()).build();
  }

  private void givenViewerLocationAndCategories() {
    Point<G2D> viewerGeometry = new Point<>(new G2D(126.9780, 37.5665), CoordinateReferenceSystems.WGS84);
    when(memberLocationRepository.findByMemberMemberId(viewerMember.getMemberId()))
        .thenReturn(Optional.of(MemberLocation.builder().member(viewerMember).geom(viewerGeometry).build()));
    when(memberItemCategoryRepository.findByMemberMemberId(viewerMember.getMemberId()))
        .thenReturn(List.of(MemberItemCategory.builder().member(viewerMember).itemCategory(ItemCategory.values()[0]).build()));
  }

  @Test
  @DisplayName("활성 롬업이 없으면 원본 페이지 그대로 (추가 조회 없음)")
  void mixPromotedItems_활성롬업없음_원본페이지() {
    when(itemRepository.existsByPromotionExpiresAtAfterAndIsDeletedFalse(any())).thenReturn(false);

    Page<Item> feedItemPage = promotedFeedService.mixPromotedItems(viewerMember, organicItemPage, SEARCH_RADIUS_IN_METERS);

    Assertions.assertSame(organicItemPage, feedItemPage);
    verifyNoInteractions(memberLocationRepository, memberItemCategoryRepository);
    verify(itemRepository, never()).findPromotedFeedCandidates(any(), anyDouble(), anyDouble(), anyDouble(), anyList(), any(), anyInt());
  }

  @Test
  @DisplayName("회원 위치가 없으면 원본 페이지 그대로")
  void mixPromotedItems_위치없음_원본페이지() {
    when(itemRepository.existsByPromotionExpiresAtAfterAndIsDeletedFalse(any())).thenReturn(true);
    when(memberLocationRepository.findByMemberMemberId(viewerMember.getMemberId())).thenReturn(Optional.empty());

    Page<Item> feedItemPage = promotedFeedService.mixPromotedItems(viewerMember, organicItemPage, SEARCH_RADIUS_IN_METERS);

    Assertions.assertSame(organicItemPage, feedItemPage);
  }

  @Test
  @DisplayName("조건에 맞는 롬업 후보가 없으면 원본 페이지 그대로")
  void mixPromotedItems_후보없음_원본페이지() {
    when(itemRepository.existsByPromotionExpiresAtAfterAndIsDeletedFalse(any())).thenReturn(true);
    givenViewerLocationAndCategories();
    when(itemRepository.findPromotedFeedCandidates(eq(viewerMember.getMemberId()), anyDouble(), anyDouble(),
        eq(SEARCH_RADIUS_IN_METERS), anyList(), any(), anyInt())).thenReturn(List.of());

    Page<Item> feedItemPage = promotedFeedService.mixPromotedItems(viewerMember, organicItemPage, SEARCH_RADIUS_IN_METERS);

    Assertions.assertSame(organicItemPage, feedItemPage);
  }

  @Test
  @DisplayName("빈 일반 페이지는 롬업 조회 없이 그대로 (피드 끝 신호 유지)")
  void mixPromotedItems_빈페이지_그대로() {
    Page<Item> emptyItemPage = new PageImpl<>(List.of(), PageRequest.of(3, 10), 25);

    Page<Item> feedItemPage = promotedFeedService.mixPromotedItems(viewerMember, emptyItemPage, SEARCH_RADIUS_IN_METERS);

    Assertions.assertSame(emptyItemPage, feedItemPage);
    verifyNoInteractions(itemRepository, memberLocationRepository, memberItemCategoryRepository);
  }

  @Test
  @DisplayName("롬업 후보가 있으면 섞인 카드 + 일반 피드 기준 페이지 정보")
  void mixPromotedItems_후보있음_섞기() {
    when(itemRepository.existsByPromotionExpiresAtAfterAndIsDeletedFalse(any())).thenReturn(true);
    givenViewerLocationAndCategories();
    Item promotedItem = createOtherMemberItem();
    when(itemRepository.findPromotedFeedCandidates(eq(viewerMember.getMemberId()), anyDouble(), anyDouble(),
        eq(SEARCH_RADIUS_IN_METERS), anyList(), any(), eq(PromotedFeedService.PROMOTED_FEED_CANDIDATE_LIMIT)))
        .thenReturn(List.of(promotedItem));

    Page<Item> feedItemPage = promotedFeedService.mixPromotedItems(viewerMember, organicItemPage, SEARCH_RADIUS_IN_METERS);

    Assertions.assertEquals(11, feedItemPage.getContent().size());
    Assertions.assertSame(promotedItem, feedItemPage.getContent().get(3));
    Assertions.assertEquals(organicItemPage.getTotalElements(), feedItemPage.getTotalElements());
    Assertions.assertEquals(organicItemPage.getNumber(), feedItemPage.getNumber());
  }
}
