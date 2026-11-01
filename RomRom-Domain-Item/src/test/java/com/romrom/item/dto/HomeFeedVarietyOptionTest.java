package com.romrom.item.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 홈 피드 다양성 옵션 단위 테스트 (#966): 미전송 시 미적용, null·중복 제거, 최대 건수 제한
 */
class HomeFeedVarietyOptionTest {

  @Test
  @DisplayName("최근 본 물품·시드 모두 없으면 다양성 미적용 (기존 정렬 그대로)")
  void of_미전송_미적용() {
    HomeFeedVarietyOption nullOption = HomeFeedVarietyOption.of(null, null);
    HomeFeedVarietyOption emptyOption = HomeFeedVarietyOption.of(List.of(), null);

    Assertions.assertSame(HomeFeedVarietyOption.none(), nullOption);
    Assertions.assertSame(HomeFeedVarietyOption.none(), emptyOption);
    Assertions.assertFalse(nullOption.hasRecentlySeenItems());
    Assertions.assertFalse(nullOption.hasFeedSeed());
  }

  @Test
  @DisplayName("null·중복 제거하고 보낸 순서 유지")
  void of_null중복제거_순서유지() {
    UUID firstSeenItemId = UUID.randomUUID();
    UUID secondSeenItemId = UUID.randomUUID();
    List<UUID> requestedSeenItemIds = Arrays.asList(firstSeenItemId, null, secondSeenItemId, firstSeenItemId);

    HomeFeedVarietyOption option = HomeFeedVarietyOption.of(requestedSeenItemIds, null);

    Assertions.assertEquals(List.of(firstSeenItemId, secondSeenItemId), option.recentlySeenItemIds());
    Assertions.assertTrue(option.hasRecentlySeenItems());
    Assertions.assertFalse(option.hasFeedSeed());
  }

  @Test
  @DisplayName("최근 본 물품은 앞쪽 최대 200건만 반영")
  void of_최대건수초과_앞쪽만반영() {
    List<UUID> requestedSeenItemIds = new ArrayList<>();
    IntStream.range(0, HomeFeedVarietyOption.MAX_RECENTLY_SEEN_ITEM_COUNT + 50)
        .forEach(index -> requestedSeenItemIds.add(UUID.randomUUID()));

    HomeFeedVarietyOption option = HomeFeedVarietyOption.of(requestedSeenItemIds, 7L);

    Assertions.assertEquals(HomeFeedVarietyOption.MAX_RECENTLY_SEEN_ITEM_COUNT, option.recentlySeenItemIds().size());
    Assertions.assertEquals(
        requestedSeenItemIds.subList(0, HomeFeedVarietyOption.MAX_RECENTLY_SEEN_ITEM_COUNT),
        option.recentlySeenItemIds());
  }

  @Test
  @DisplayName("시드만 보내도 다양성 적용")
  void of_시드만_적용() {
    HomeFeedVarietyOption option = HomeFeedVarietyOption.of(null, 12345L);

    Assertions.assertTrue(option.hasFeedSeed());
    Assertions.assertEquals(12345L, option.feedSeed());
    Assertions.assertFalse(option.hasRecentlySeenItems());
  }
}
