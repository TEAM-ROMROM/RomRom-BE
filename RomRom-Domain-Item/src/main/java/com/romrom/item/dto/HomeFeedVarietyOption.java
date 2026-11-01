package com.romrom.item.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 홈 피드 다양성 옵션 (#966): 최근 본 물품 뒤로 보내기, 추천순 시드 섞기
 * 두 값 모두 없으면 기존 정렬 그대로 (구버전 앱 호환)
 */
public record HomeFeedVarietyOption(List<UUID> recentlySeenItemIds, Long feedSeed) {

  // 정렬에 반영하는 최근 본 물품 최대 수 (초과분은 앞쪽 200건만 사용)
  public static final int MAX_RECENTLY_SEEN_ITEM_COUNT = 200;

  private static final HomeFeedVarietyOption NONE = new HomeFeedVarietyOption(List.of(), null);

  public HomeFeedVarietyOption {
    recentlySeenItemIds = recentlySeenItemIds == null ? List.of() : List.copyOf(recentlySeenItemIds);
  }

  /**
   * 기존 정렬 그대로 (다양성 미적용)
   */
  public static HomeFeedVarietyOption none() {
    return NONE;
  }

  /**
   * 요청값 > 옵션 (null 제거, 중복 제거, 순서 유지, 최대 200건)
   */
  public static HomeFeedVarietyOption of(List<UUID> requestedRecentlySeenItemIds, Long requestedFeedSeed) {
    if ((requestedRecentlySeenItemIds == null || requestedRecentlySeenItemIds.isEmpty()) && requestedFeedSeed == null) {
      return NONE;
    }
    Set<UUID> distinctSeenItemIds = new LinkedHashSet<>();
    if (requestedRecentlySeenItemIds != null) {
      requestedRecentlySeenItemIds.stream()
          .filter(Objects::nonNull)
          .forEach(distinctSeenItemIds::add);
    }
    List<UUID> cappedSeenItemIds = new ArrayList<>(distinctSeenItemIds)
        .subList(0, Math.min(distinctSeenItemIds.size(), MAX_RECENTLY_SEEN_ITEM_COUNT));
    return new HomeFeedVarietyOption(cappedSeenItemIds, requestedFeedSeed);
  }

  public boolean hasRecentlySeenItems() {
    return !recentlySeenItemIds.isEmpty();
  }

  public boolean hasFeedSeed() {
    return feedSeed != null;
  }
}
