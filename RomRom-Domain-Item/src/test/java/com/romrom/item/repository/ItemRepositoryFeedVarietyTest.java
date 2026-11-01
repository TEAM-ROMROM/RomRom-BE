package com.romrom.item.repository;

import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.item.dto.HomeFeedVarietyOption;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.web.RomBackApplication;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 피드 다양성 쿼리 통합 테스트 (#966): 최근 본 물품 뒤로, 시드 섞기, 미전송 시 기존 순서
 * dev 프로파일(실제 PostgreSQL) 필요: hashtext·CASE 정렬은 H2로 재현 불가. 테스트 물품은 롤백
 */
@Tag("integration")
@SpringBootTest(classes = RomBackApplication.class, properties = {
    // dev 프로파일에 추천 가중치 미설정 > 운영값과 같은 비율로 지정
    "recommendation.weight.like=5.0",
    "recommendation.weight.view=1.0",
    "recommendation.weight.category=0.6",
    "recommendation.weight.freshness=0.4",
    "recommendation.weight.explicit=0.4",
    "recommendation.weight.implicit=0.6",
    "recommendation.like-multiplier=5.0",
    "recommendation.time-decay-lambda=0.1"
})
@ActiveProfiles("dev")
@Transactional
class ItemRepositoryFeedVarietyTest {

  private static final int SEEDED_ITEM_COUNT = 20;
  private static final int SMALL_PAGE_SIZE = 5;
  private static final int WHOLE_FEED_PAGE_SIZE = 1000;

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private MemberRepository memberRepository;

  // 본인 물품 제외 조건에 걸리지 않는 임의 조회 회원
  private final UUID viewerMemberId = UUID.randomUUID();
  private List<UUID> seededItemIds;

  @BeforeEach
  void seedFeedItems() {
    String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
    Member ownerMember = memberRepository.save(Member.builder()
        .email("feed-variety-" + uniqueSuffix + "@test.local")
        .nickname("feed-variety-" + uniqueSuffix)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build());
    seededItemIds = IntStream.range(0, SEEDED_ITEM_COUNT)
        .mapToObj(index -> itemRepository.save(Item.builder()
            .member(ownerMember)
            .itemName("feed-variety-item-" + index)
            .itemCategory(ItemCategory.values()[index % ItemCategory.values().length])
            .itemCondition(ItemCondition.values()[0])
            .itemStatus(ItemStatus.AVAILABLE)
            .build()).getItemId())
        .toList();
    itemRepository.flush();
  }

  /**
   * 피드 조회 > 이번 테스트가 넣은 물품만 남긴 ID 목록 (로컬 DB 기존 물품 영향 제거)
   */
  private List<UUID> fetchSeededItemIds(ItemSortField sortField, HomeFeedVarietyOption option, int pageNumber, int pageSize) {
    return itemRepository.filterItems(
            viewerMemberId, null, null, null, null, null, null, sortField, option,
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, sortField.getProperty())))
        .getContent()
        .stream()
        .map(Item::getItemId)
        .filter(seededItemIds::contains)
        .toList();
  }

  @Test
  @DisplayName("최근 본 물품은 걸러지지 않고 목록 맨 뒤로 이동")
  void filterItems_최근본물품_맨뒤로() {
    List<UUID> baselineItemIds = fetchSeededItemIds(ItemSortField.CREATED_DATE, HomeFeedVarietyOption.none(), 0, WHOLE_FEED_PAGE_SIZE);
    Assertions.assertEquals(SEEDED_ITEM_COUNT, baselineItemIds.size());
    List<UUID> recentlySeenItemIds = baselineItemIds.subList(0, 3);

    List<UUID> variedItemIds = fetchSeededItemIds(
        ItemSortField.CREATED_DATE, HomeFeedVarietyOption.of(recentlySeenItemIds, null), 0, WHOLE_FEED_PAGE_SIZE);

    // 개수 동일 (걸러내지 않음), 안 본 물품은 기존 순서 유지 후 본 물품 3건이 맨 뒤
    Assertions.assertEquals(SEEDED_ITEM_COUNT, variedItemIds.size());
    Assertions.assertEquals(baselineItemIds.subList(3, SEEDED_ITEM_COUNT), variedItemIds.subList(0, SEEDED_ITEM_COUNT - 3));
    Assertions.assertEquals(new HashSet<>(recentlySeenItemIds),
        new HashSet<>(variedItemIds.subList(SEEDED_ITEM_COUNT - 3, SEEDED_ITEM_COUNT)));
  }

  @Test
  @DisplayName("미전송이면 두 번 조회해도 기존 추천순 그대로 (시드 섞기 없음)")
  void filterItems_미전송_기존추천순() {
    List<UUID> firstItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, HomeFeedVarietyOption.none(), 0, WHOLE_FEED_PAGE_SIZE);
    List<UUID> secondItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, HomeFeedVarietyOption.none(), 0, WHOLE_FEED_PAGE_SIZE);

    Assertions.assertEquals(SEEDED_ITEM_COUNT, firstItemIds.size());
    Assertions.assertEquals(firstItemIds, secondItemIds);
  }

  @Test
  @DisplayName("같은 시드면 페이지를 나눠 받아도 한 번에 받은 순서와 동일")
  void filterItems_같은시드_페이지간순서일관() {
    HomeFeedVarietyOption option = HomeFeedVarietyOption.of(null, 424242L);
    List<UUID> wholeItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, option, 0, WHOLE_FEED_PAGE_SIZE);

    // 로컬 DB에 다른 물품이 있으면 페이지 경계가 섞이므로 전체 피드 기준으로 비교
    List<UUID> allPagedItemIds = new ArrayList<>();
    for (int pageNumber = 0; ; pageNumber++) {
      List<Item> pageItems = itemRepository.filterItems(
              viewerMemberId, null, null, null, null, null, null, ItemSortField.RECOMMENDED, option,
              PageRequest.of(pageNumber, SMALL_PAGE_SIZE, Sort.by(Sort.Direction.DESC, ItemSortField.RECOMMENDED.getProperty())))
          .getContent();
      if (pageItems.isEmpty()) {
        break;
      }
      pageItems.stream().map(Item::getItemId).filter(seededItemIds::contains).forEach(allPagedItemIds::add);
    }

    Assertions.assertEquals(wholeItemIds, allPagedItemIds);
    Assertions.assertEquals(wholeItemIds, fetchSeededItemIds(ItemSortField.RECOMMENDED, option, 0, WHOLE_FEED_PAGE_SIZE));
  }

  @Test
  @DisplayName("시드가 다르면 추천순 순서가 달라지고, 물품 구성은 동일")
  void filterItems_다른시드_순서변경() {
    List<UUID> unshuffledItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, HomeFeedVarietyOption.none(), 0, WHOLE_FEED_PAGE_SIZE);
    List<UUID> firstSeedItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, HomeFeedVarietyOption.of(null, 1L), 0, WHOLE_FEED_PAGE_SIZE);
    List<UUID> secondSeedItemIds = fetchSeededItemIds(ItemSortField.RECOMMENDED, HomeFeedVarietyOption.of(null, 2L), 0, WHOLE_FEED_PAGE_SIZE);

    Assertions.assertNotEquals(firstSeedItemIds, secondSeedItemIds);
    Assertions.assertNotEquals(unshuffledItemIds, firstSeedItemIds);
    Assertions.assertEquals(new HashSet<>(unshuffledItemIds), new HashSet<>(firstSeedItemIds));
  }
}
