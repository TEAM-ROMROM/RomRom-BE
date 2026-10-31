package com.romrom.item.service;

import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 홈 피드 롬업 섞기 단위 테스트 (#819): 비율, 연속 없음, 본인·중복 제외, 후보 없으면 원본 그대로
 */
class PromotedFeedMixerTest {

  private static final UUID VIEWER_MEMBER_ID = UUID.randomUUID();

  private static Item createItem(UUID ownerMemberId) {
    return Item.builder()
        .itemId(UUID.randomUUID())
        .member(Member.builder().memberId(ownerMemberId).build())
        .build();
  }

  private static List<Item> createOtherMemberItems(int itemCount) {
    return IntStream.range(0, itemCount).mapToObj(index -> createItem(UUID.randomUUID())).collect(Collectors.toList());
  }

  private static boolean isPromotedCard(Item card, List<Item> promotedItems) {
    return promotedItems.contains(card);
  }

  @Test
  @DisplayName("롬업 후보가 없으면 일반 목록 그대로 반환")
  void mixPromotedItems_후보없음_원본그대로() {
    List<Item> organicItems = createOtherMemberItems(10);

    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(organicItems, List.of(), VIEWER_MEMBER_ID, 0);

    Assertions.assertSame(organicItems, mixedItems);
  }

  @Test
  @DisplayName("일반 카드가 비면 롬업도 넣지 않음 (빈 페이지 = 피드 끝 신호 유지)")
  void mixPromotedItems_빈페이지_그대로() {
    List<Item> organicItems = new ArrayList<>();

    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(organicItems, createOtherMemberItems(5), VIEWER_MEMBER_ID, 0);

    Assertions.assertTrue(mixedItems.isEmpty());
  }

  @Test
  @DisplayName("일반 3장마다 롬업 1장: 일반 10장 > 롬업 3장, 4번째·8번째·12번째 자리")
  void mixPromotedItems_비율_4장중1장() {
    List<Item> organicItems = createOtherMemberItems(10);
    List<Item> promotedItems = createOtherMemberItems(10);

    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(organicItems, promotedItems, VIEWER_MEMBER_ID, 0);

    Assertions.assertEquals(13, mixedItems.size());
    List<Integer> promotedPositions = IntStream.range(0, mixedItems.size())
        .filter(position -> isPromotedCard(mixedItems.get(position), promotedItems))
        .boxed().toList();
    Assertions.assertEquals(List.of(3, 7, 11), promotedPositions);
    // 일반 카드 순서 유지
    Assertions.assertEquals(organicItems,
        mixedItems.stream().filter(card -> !isPromotedCard(card, promotedItems)).toList());
  }

  @Test
  @DisplayName("롬업 카드는 연속으로 나오지 않음 (후보·페이지 크기 여러 조합)")
  void mixPromotedItems_연속롬업없음() {
    for (int organicCount = 0; organicCount <= 30; organicCount++) {
      List<Item> organicItems = createOtherMemberItems(organicCount);
      List<Item> promotedItems = createOtherMemberItems(20);

      List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(organicItems, promotedItems, VIEWER_MEMBER_ID, 2);

      for (int position = 1; position < mixedItems.size(); position++) {
        boolean isConsecutivePromoted = isPromotedCard(mixedItems.get(position - 1), promotedItems)
            && isPromotedCard(mixedItems.get(position), promotedItems);
        Assertions.assertFalse(isConsecutivePromoted, "연속 롬업 발생: organicCount=" + organicCount);
      }
      long promotedCount = mixedItems.stream().filter(card -> isPromotedCard(card, promotedItems)).count();
      Assertions.assertTrue(promotedCount * 4 <= mixedItems.size(), "25% 초과: organicCount=" + organicCount);
    }
  }

  @Test
  @DisplayName("본인 물품과 이 페이지에 이미 있는 물품은 롬업으로 넣지 않음")
  void mixPromotedItems_본인물품_중복제외() {
    List<Item> organicItems = createOtherMemberItems(6);
    Item viewerOwnPromotedItem = createItem(VIEWER_MEMBER_ID);
    Item alreadyInPagePromotedItem = organicItems.get(4);
    Item eligiblePromotedItem = createItem(UUID.randomUUID());

    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(organicItems,
        List.of(viewerOwnPromotedItem, alreadyInPagePromotedItem, eligiblePromotedItem), VIEWER_MEMBER_ID, 0);

    Assertions.assertFalse(mixedItems.contains(viewerOwnPromotedItem));
    Assertions.assertEquals(1, mixedItems.stream().filter(card -> card == alreadyInPagePromotedItem).count());
    Assertions.assertEquals(7, mixedItems.size());
    Assertions.assertSame(eligiblePromotedItem, mixedItems.get(3));
  }

  @Test
  @DisplayName("후보가 본인 물품뿐이면 원본 그대로")
  void mixPromotedItems_본인물품만_원본그대로() {
    List<Item> organicItems = createOtherMemberItems(9);

    List<Item> mixedItems = PromotedFeedMixer.mixPromotedItems(
        organicItems, List.of(createItem(VIEWER_MEMBER_ID)), VIEWER_MEMBER_ID, 0);

    Assertions.assertSame(organicItems, mixedItems);
  }

  @Test
  @DisplayName("페이지마다 다른 롬업 물품부터 회전, 한 페이지 안 중복 없음")
  void mixPromotedItems_페이지회전() {
    List<Item> organicItems = createOtherMemberItems(9);
    List<Item> promotedItems = createOtherMemberItems(5);

    List<Item> firstPageItems = PromotedFeedMixer.mixPromotedItems(organicItems, promotedItems, VIEWER_MEMBER_ID, 0);
    List<Item> secondPageItems = PromotedFeedMixer.mixPromotedItems(organicItems, promotedItems, VIEWER_MEMBER_ID, 1);

    List<Item> firstPagePromoted = firstPageItems.stream().filter(promotedItems::contains).toList();
    List<Item> secondPagePromoted = secondPageItems.stream().filter(promotedItems::contains).toList();
    Assertions.assertEquals(promotedItems.subList(0, 3), firstPagePromoted);
    Assertions.assertEquals(List.of(promotedItems.get(3), promotedItems.get(4), promotedItems.get(0)), secondPagePromoted);
    Assertions.assertEquals(3, Set.copyOf(secondPagePromoted).size());
  }
}
