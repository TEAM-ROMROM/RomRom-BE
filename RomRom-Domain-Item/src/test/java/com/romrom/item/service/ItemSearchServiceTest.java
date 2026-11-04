package com.romrom.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemSearchSortField;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemSearchCondition;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberLocationRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 물품 검색 요청 정리: 검색어 검증·자르기, 카테고리 중복 제거, 정렬 기본값, 거리순 위치 폴백, 페이지 보정 (#862)
 */
@ExtendWith(MockitoExtension.class)
class ItemSearchServiceTest {

  @Mock private ItemRepository itemRepository;
  @Mock private MemberLocationRepository memberLocationRepository;
  @InjectMocks private ItemSearchService itemSearchService;

  private final UUID viewerMemberId = UUID.randomUUID();

  @Test
  @DisplayName("빈 검색어·공백만 > INVALID_SEARCH_KEYWORD, 조회 안 함")
  void rejectsBlankKeyword() {
    for (String blankKeyword : new String[]{null, "", "   "}) {
      CustomException exception = assertThrows(CustomException.class,
          () -> itemSearchService.searchItems(buildRequest(blankKeyword), Set.of()));
      assertEquals(ErrorCode.INVALID_SEARCH_KEYWORD, exception.getErrorCode());
    }
    verify(itemRepository, never()).searchVisibleItems(any(), any());
  }

  @Test
  @DisplayName("검색어 앞뒤 공백 제거, 50자 초과분 잘라냄")
  void normalizesKeyword() {
    assertEquals("아이폰", ItemSearchService.normalizeSearchKeyword("  아이폰 "));
    assertEquals(ItemSearchService.MAXIMUM_SEARCH_KEYWORD_LENGTH,
        ItemSearchService.normalizeSearchKeyword("가".repeat(80)).length());
  }

  @Test
  @DisplayName("정렬 미전송 > 최신순, 카테고리 null·중복 제거, 제외 물품 전달, 페이지 크기 상한")
  void buildsSearchCondition() {
    ItemRequest request = buildRequest(" 신발 ");
    request.setSearchItemCategories(java.util.Arrays.asList(ItemCategory.SHOES, null, ItemCategory.SHOES, ItemCategory.BEAUTY));
    request.setPageSize(500);
    Set<UUID> reportedItemIds = Set.of(UUID.randomUUID());
    when(itemRepository.searchVisibleItems(any(), any())).thenReturn(Page.empty());

    itemSearchService.searchItems(request, reportedItemIds);

    ArgumentCaptor<ItemSearchCondition> conditionCaptor = ArgumentCaptor.forClass(ItemSearchCondition.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(itemRepository).searchVisibleItems(conditionCaptor.capture(), pageableCaptor.capture());
    ItemSearchCondition searchCondition = conditionCaptor.getValue();
    assertEquals("신발", searchCondition.keyword());
    assertEquals(ItemSearchSortField.LATEST, searchCondition.sortField());
    assertEquals(List.of(ItemCategory.SHOES, ItemCategory.BEAUTY), searchCondition.itemCategories());
    assertEquals(reportedItemIds, searchCondition.excludedItemIds());
    assertEquals(viewerMemberId, searchCondition.viewerMemberId());
    assertEquals(ItemSearchService.MAXIMUM_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    verify(memberLocationRepository, never()).findByMemberMemberId(any());
  }

  @Test
  @DisplayName("거리순 + 위치 미등록 > 좌표 없이 조회 (최신순 폴백)")
  void distanceSortWithoutLocationFallsBack() {
    ItemRequest request = buildRequest("책");
    request.setSearchSortField(ItemSearchSortField.DISTANCE);
    when(memberLocationRepository.findByMemberMemberId(viewerMemberId)).thenReturn(Optional.empty());
    when(itemRepository.searchVisibleItems(any(), any())).thenReturn(Page.empty());

    itemSearchService.searchItems(request, Set.of());

    ArgumentCaptor<ItemSearchCondition> conditionCaptor = ArgumentCaptor.forClass(ItemSearchCondition.class);
    verify(itemRepository).searchVisibleItems(conditionCaptor.capture(), any());
    assertNull(conditionCaptor.getValue().longitude());
    assertEquals(false, conditionCaptor.getValue().hasLocation());
  }

  private ItemRequest buildRequest(String searchKeyword) {
    ItemRequest request = new ItemRequest();
    request.setMember(Member.builder().memberId(viewerMemberId).build());
    request.setSearchKeyword(searchKeyword);
    return request;
  }
}
