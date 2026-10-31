package com.romrom.item.service;

import com.romrom.item.entity.postgres.Item;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 홈 피드 롬업 카드 섞기 (#819): 일반 3장마다 롬업 1장 (4장 중 1장, 25%)
 * 규칙 4개: 연속 롬업 없음, 본인 물품 제외, 이 페이지에 이미 있는 물품 제외, 후보 없으면 원본 그대로
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromotedFeedMixer {

  // 롬업 1장 앞에 오는 일반 카드 수 (일반 3 + 롬업 1 = 4장 중 1장)
  public static final int ORGANIC_CARDS_PER_PROMOTED_CARD = 3;

  /**
   * 일반 카드 + 롬업 후보 > 섞인 카드 목록 (롬업은 페이지 번호로 회전해 페이지마다 다른 물품 우선)
   */
  public static List<Item> mixPromotedItems(
      List<Item> organicItems, List<Item> promotedItemCandidates, UUID viewerMemberId, int pageNumber) {
    List<Item> eligiblePromotedItems = filterEligiblePromotedItems(organicItems, promotedItemCandidates, viewerMemberId);
    int promotedSlotCount = organicItems.size() / ORGANIC_CARDS_PER_PROMOTED_CARD;
    if (eligiblePromotedItems.isEmpty() || promotedSlotCount == 0) {
      return organicItems;
    }
    List<Item> selectedPromotedItems = selectRotatedPromotedItems(eligiblePromotedItems, promotedSlotCount, pageNumber);

    // 일반 3장 뒤마다 롬업 1장 삽입 (첫 카드는 항상 일반)
    List<Item> mixedItems = new ArrayList<>(organicItems.size() + selectedPromotedItems.size());
    int nextPromotedIndex = 0;
    for (int organicIndex = 0; organicIndex < organicItems.size(); organicIndex++) {
      mixedItems.add(organicItems.get(organicIndex));
      boolean isPromotedSlot = (organicIndex + 1) % ORGANIC_CARDS_PER_PROMOTED_CARD == 0;
      if (isPromotedSlot && nextPromotedIndex < selectedPromotedItems.size()) {
        mixedItems.add(selectedPromotedItems.get(nextPromotedIndex++));
      }
    }
    return mixedItems;
  }

  /**
   * 후보 정리: 본인 물품·이 페이지 일반 카드와 중복·후보 내 중복 제외 (순서 유지)
   */
  private static List<Item> filterEligiblePromotedItems(
      List<Item> organicItems, List<Item> promotedItemCandidates, UUID viewerMemberId) {
    Set<UUID> organicItemIds = new HashSet<>();
    organicItems.forEach(organicItem -> organicItemIds.add(organicItem.getItemId()));

    Map<UUID, Item> eligiblePromotedItemById = new LinkedHashMap<>();
    for (Item promotedItemCandidate : promotedItemCandidates) {
      boolean isViewerOwnItem = promotedItemCandidate.getMember() != null
          && promotedItemCandidate.getMember().getMemberId().equals(viewerMemberId);
      boolean isAlreadyInPage = organicItemIds.contains(promotedItemCandidate.getItemId());
      if (!isViewerOwnItem && !isAlreadyInPage) {
        eligiblePromotedItemById.putIfAbsent(promotedItemCandidate.getItemId(), promotedItemCandidate);
      }
    }
    return new ArrayList<>(eligiblePromotedItemById.values());
  }

  /**
   * 페이지 번호 기준 회전 선택 (page N은 N * 슬롯 수 위치부터, 한 페이지 안 중복 없음)
   */
  private static List<Item> selectRotatedPromotedItems(
      List<Item> eligiblePromotedItems, int promotedSlotCount, int pageNumber) {
    int selectedCount = Math.min(promotedSlotCount, eligiblePromotedItems.size());
    int rotationStartIndex = Math.floorMod((long) pageNumber * promotedSlotCount, eligiblePromotedItems.size());

    List<Item> selectedPromotedItems = new ArrayList<>(selectedCount);
    for (int offset = 0; offset < selectedCount; offset++) {
      selectedPromotedItems.add(eligiblePromotedItems.get((rotationStartIndex + offset) % eligiblePromotedItems.size()));
    }
    return selectedPromotedItems;
  }
}
