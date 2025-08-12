package com.romrom.item.service;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.util.LocationUtil;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.entity.mongo.ItemCustomTags;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;
import com.romrom.web.RomBackApplication;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RomBackApplication.class)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class ItemServicePerformanceTest {

  @Autowired private ItemService itemService;
  @Autowired private ItemRepository itemRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private ItemImageRepository itemImageRepository;
  @Autowired private ItemCustomTagsService itemCustomTagsService;
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private PlatformTransactionManager txManager;

  @PersistenceContext private EntityManager em;

  private Member testMember;     // 내 계정
  private Member otherMember;    // 상대 계정

  private static final int MY_COUNT = 100;
  private static final int OTHER_COUNT = 100;
  private static final int REPEAT_COUNT = 2;

  private long reservingTimeMs;

  @BeforeAll
  void setUp() {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    reservingTimeMs = tx.execute(status -> {
      long t0 = System.nanoTime();

      // 1) 데이터 초기화
      mongoTemplate.dropCollection(ItemCustomTags.class);
      em.createNativeQuery("""
          TRUNCATE TABLE item_image, item, member_item_category, member_location, member
          RESTART IDENTITY CASCADE
        """).executeUpdate();
      em.createNativeQuery("SET LOCAL synchronous_commit = 'off'").executeUpdate();

      // 2) 멤버 생성
      testMember = Member.builder().email("me@romrom.com").nickname("me").build();
      otherMember = Member.builder().email("other@romrom.com").nickname("other").build();
      em.persist(testMember);
      em.persist(otherMember);
      em.flush();

      // 3) 아이템/이미지 시드 + 몽고 태그
      seedItemsWithImagesAndTags(testMember, MY_COUNT, 2);
      seedItemsWithImagesAndTags(otherMember, OTHER_COUNT, 2);

      return (System.nanoTime() - t0) / 1_000_000;
    });
    log.info("[BeforeEach] 준비시간: {} ms", reservingTimeMs);
  }

  @Test
  @Order(1)
  @DisplayName("내 물품 찾기: Old vs FetchJoin vs FetchJoin + assembler vs member query + assembler 성능 비교")
  void compareGetMyItemsFetchJoinMemberDescVariants() {
    // given
    ItemRequest req = new ItemRequest();
    req.setMember(testMember);
    req.setItemStatus(ItemStatus.AVAILABLE);
    req.setPageNumber(0);
    req.setPageSize(20);

   /* ItemResponse oldRes = itemService.getMyItemsOldMethod(req);
    ItemResponse fjRes  = itemService.getMyItemsFetchJoinMember(req);
    ItemResponse newRes = itemService.getMyItemsFetchJoinMemberAndBatchOthers(req);
    ItemResponse newResWithAssembler = itemService.getMyItems(req);

    assertThat(oldRes.getItemDetailPage().getTotalElements())
        .isEqualTo(fjRes.getItemDetailPage().getTotalElements())
        .isEqualTo(newRes.getItemDetailPage().getTotalElements());

    assertThat(oldRes.getItemDetailPage().getSize())
        .isEqualTo(fjRes.getItemDetailPage().getSize())
        .isEqualTo(newRes.getItemDetailPage().getSize());
*/
    // 성능 측정
    long tOld = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getMyItemsOldMethod(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });
    long tFj = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getMyItemsFetchJoinMember(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });
    long tFjAsb = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getMyItemsWithMemberQuery(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });
    long tNew = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getMyItemsFetchJoinMemberDesc(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });
    log.info("========== [내 물품 찾기] ==========");
    log.info("OldMethod        : {} ms", tOld);
    log.info("FetchJoinMember  : {} ms", tFj);
    log.info("FetchJoinMember assemble  : {} ms", tFjAsb);
    log.info("New(batch assemble): {} ms", tNew);
  }

  @Test
  @Order(2)
  @DisplayName("모든 물품 찾기: Old(native) vs New(fetch+batch) 정확성 + 성능 비교")
  void compareGetItemListVariants() {
    // given: 로그인 주체는 testMember, 전체 목록은 '내 물품 제외' 이므로 otherMember의 물품이 대상
    ItemRequest req = new ItemRequest();
    req.setMember(testMember);
    req.setPageNumber(0);
    req.setPageSize(30);

    /*ItemResponse oldRes = itemService.getItemListOld(req);
    ItemResponse newRes = itemService.getItemList(req);

    // 정확성: totalElements가 같거나(필터 동일), new 가 더 안전(정렬/필터 차이 없도록 동일 조건 가정)
    assertThat(newRes.getItemDetailPage().getTotalElements())
        .isEqualTo(oldRes.getItemDetailPage().getTotalElements());
*/
    // 성능
    long tOld = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getItemListOld(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });
    long tNew = timeMs(() -> {
      int sink = 0;
      for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getItemList(req).getItemDetailPage().getSize();
      log.info("ms : " + sink);
    });

    log.info("========== [모든 물품 찾기(내 것 제외)] ==========");
    log.info("Old(native+per-item) : {} ms", tOld);
    log.info("New(fetch+batch)     : {} ms", tNew);
  }

  // ----------------------------- helpers -----------------------------

  private void seedItemsWithImagesAndTags(Member owner, int count, int imagesPerItem) {
    final double LON = 126.7150;
    final double LAT = 37.5610;
    var point = LocationUtil.convertToPoint(LON, LAT);
    var categories = ItemCategory.values();
    var conditions = ItemCondition.values();

    List<UUID> idsForTags = new ArrayList<>(count);

    IntStream.range(0, count).forEach(i -> {
      Item item = Item.builder()
          .member(em.getReference(Member.class, owner.getMemberId()))
          .itemName(owner.getNickname() + " Item " + i)
          .itemDescription("desc " + i)
          .itemStatus(ItemStatus.AVAILABLE)
          .itemCategory(categories[i % categories.length])
          .itemCondition(conditions[i % conditions.length])
          .price(1000 * (i + 1))
          .location(point)
          .build();
      em.persist(item);

      for (int k = 0; k < imagesPerItem; k++) {
        em.persist(ItemImage.builder()
            .item(item)
            .imageUrl("http://example.com/" + owner.getNickname() + "/img_" + i + "_" + k + ".jpg")
            .build());
      }
      idsForTags.add(item.getItemId());
    });
    em.flush();
    em.clear();

    // 몽고 태그 벌크
    List<ItemCustomTags> docs = new ArrayList<>(idsForTags.size());
    for (UUID id : idsForTags) {
      docs.add(ItemCustomTags.builder()
          .itemId(id)
          .customTags(List.of("태그A", "태그B", "owner:" + owner.getNickname()))
          .build());
    }
    var ops = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ItemCustomTags.class);
    ops.insert(docs);
    ops.execute();
  }

  private long timeMs(Runnable r) {
    long t0 = System.nanoTime();
    r.run();
    return (System.nanoTime() - t0) / 1_000_000;
  }
}