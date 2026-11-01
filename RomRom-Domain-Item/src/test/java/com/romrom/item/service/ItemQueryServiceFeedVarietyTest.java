package com.romrom.item.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.ItemSortField;
import com.romrom.item.dto.HomeFeedVarietyOption;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 홈 피드 목록 다양성 옵션 전달 단위 테스트 (#966): 미전송 시 기존 조회 그대로, 전송 시 정리된 옵션 전달
 */
@ExtendWith(MockitoExtension.class)
class ItemQueryServiceFeedVarietyTest {

  @Mock
  private ItemRepository itemRepository;

  @Mock
  private PromotedFeedService promotedFeedService;

  @InjectMocks
  private ItemQueryService itemQueryService;

  private Member viewerMember;
  private Page<Item> organicItemPage;

  @BeforeEach
  void setUp() {
    viewerMember = Member.builder().memberId(UUID.randomUUID()).build();
    organicItemPage = new PageImpl<>(List.of(Item.builder().itemId(UUID.randomUUID()).build()), PageRequest.of(0, 10), 1);
    when(itemRepository.filterItems(
        any(), isNull(), isNull(), anyDouble(), isNull(), isNull(), isNull(), eq(ItemSortField.CREATED_DATE), any(), any()))
        .thenReturn(organicItemPage);
    when(promotedFeedService.mixPromotedItems(any(), any(), anyDouble())).thenAnswer(invocation -> invocation.getArgument(1));
  }

  private ItemRequest createLatestFeedRequest() {
    ItemRequest request = new ItemRequest();
    request.setMember(viewerMember);
    request.setPageNumber(1);
    request.setPageSize(10);
    request.setSortField(ItemSortField.CREATED_DATE);
    return request;
  }

  private HomeFeedVarietyOption captureHomeFeedVarietyOption() {
    ArgumentCaptor<HomeFeedVarietyOption> optionCaptor = ArgumentCaptor.forClass(HomeFeedVarietyOption.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(itemRepository).filterItems(
        eq(viewerMember.getMemberId()), isNull(), isNull(), eq(10000.0), isNull(), isNull(), isNull(),
        eq(ItemSortField.CREATED_DATE), optionCaptor.capture(), pageableCaptor.capture());
    // 페이지 번호·크기는 다양성 옵션과 무관하게 요청값 그대로
    Assertions.assertEquals(1, pageableCaptor.getValue().getPageNumber());
    Assertions.assertEquals(10, pageableCaptor.getValue().getPageSize());
    return optionCaptor.getValue();
  }

  @Test
  @DisplayName("최근 본 물품·시드 미전송 (구버전 앱) > 다양성 미적용 옵션으로 기존 조회 그대로")
  void getItemList_미전송_기존조회그대로() {
    Page<Item> responseItemPage = itemQueryService.getItemList(createLatestFeedRequest()).getItemPage();

    HomeFeedVarietyOption option = captureHomeFeedVarietyOption();
    Assertions.assertSame(HomeFeedVarietyOption.none(), option);
    Assertions.assertSame(organicItemPage, responseItemPage);
  }

  @Test
  @DisplayName("최근 본 물품·시드 전송 > 중복 제거된 옵션 그대로 저장소에 전달")
  void getItemList_전송_정리된옵션전달() {
    UUID firstSeenItemId = UUID.randomUUID();
    UUID secondSeenItemId = UUID.randomUUID();
    ItemRequest request = createLatestFeedRequest();
    request.setRecentlySeenItemIds(List.of(firstSeenItemId, secondSeenItemId, firstSeenItemId));
    request.setFeedSeed(98765L);

    itemQueryService.getItemList(request);

    HomeFeedVarietyOption option = captureHomeFeedVarietyOption();
    Assertions.assertEquals(List.of(firstSeenItemId, secondSeenItemId), option.recentlySeenItemIds());
    Assertions.assertEquals(98765L, option.feedSeed());
  }
}
