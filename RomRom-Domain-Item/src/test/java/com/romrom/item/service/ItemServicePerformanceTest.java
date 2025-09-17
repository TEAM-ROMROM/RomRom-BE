package com.romrom.item.service;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.util.LocationUtil;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.mongo.ItemCustomTags;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.member.entity.Member;
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
  @Autowired private MongoTemplate mongoTemplate;
  @Autowired private PlatformTransactionManager txManager;

  @PersistenceContext private EntityManager em;

  private Member testMember;     // 내 계정
  private Member otherMember;    // 상대 계정

  private static final int REPEAT_COUNT = 5;

  private long reservingTimeMs;

  @BeforeAll
  void setUp() {
    TransactionTemplate tx = new TransactionTemplate(txManager);
    reservingTimeMs = tx.execute(status -> {
      long t0 = System.nanoTime();

      // 0) Mongo 초기화
      mongoTemplate.dropCollection(ItemCustomTags.class);

      // 1) RDB 초기화
      em.createNativeQuery("""
      TRUNCATE TABLE item_image, item, member_item_category, member_location, member
      RESTART IDENTITY CASCADE
    """).executeUpdate();

      // 1-1) 확장 모듈 (UUID, PostGIS) 보장
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pgcrypto").executeUpdate();
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS postgis").executeUpdate();

      // 1-2) 과거 남은 CHECK 제약으로 인한 충돌 회피 (테스트 DB 한정)
      em.createNativeQuery("ALTER TABLE IF EXISTS item DROP CONSTRAINT IF EXISTS item_item_status_check").executeUpdate();
      em.createNativeQuery("ALTER TABLE IF EXISTS item DROP CONSTRAINT IF EXISTS item_item_condition_check").executeUpdate();

      // 1-3) write latency 줄이기
      em.createNativeQuery("SET LOCAL synchronous_commit = 'off'").executeUpdate();

      // -----------------------------
      // 파라미터: 유저/아이템/이미지 규모
      // -----------------------------
      final int NUM_USERS = 10000;         // 사용자 수
      final int ITEMS_PER_USER = 100;    // 1인당 아이템 수
      final int IMAGES_PER_ITEM = 2;     // 아이템당 이미지 수
      final int TOTAL_ITEMS = NUM_USERS * ITEMS_PER_USER;

      // 2) 멤버 대량 생성 (NotNull boolean들 명시 세팅)
      em.createNativeQuery("""
      INSERT INTO member
        (member_id, email, nickname,
         created_date, updated_date,
         is_first_login, is_item_category_saved, is_first_item_posted,
         is_member_location_saved, is_required_terms_agreed, is_marketing_info_agreed,
         is_deleted)
      SELECT gen_random_uuid(),
             'user'||g||'@romrom.com',
             'user'||g,
             now(), now(),
             true, false, false,
             false, false, false,
             false
      FROM generate_series(0, :n_users - 1) AS g
    """).setParameter("n_users", NUM_USERS).executeUpdate();

      // 3) 아이템 대량 생성
      //  - item_status: AVAILABLE / EXCHANGED
      //  - item_condition: SEALED / SLIGHTLY_USED / MODERATELY_USED / HEAVILY_USED
      //  - item_category: 1..26 (ProductCategoryConverter 매핑)
      em.createNativeQuery("""
      WITH m AS (
        SELECT member_id, row_number() OVER (ORDER BY created_date, member_id) - 1 AS rn
        FROM member
        ORDER BY created_date, member_id
        LIMIT :n_users
      ),
      g AS (
        SELECT m.member_id,
               gs AS idx,
               floor(gs / :items_per_user)::int AS user_rn
        FROM m
        JOIN generate_series(0, :total_items - 1) AS gs
          ON floor(gs / :items_per_user) = m.rn
      )
      INSERT INTO item (
        item_id, member_member_id, item_name, item_description,
        item_status, item_category, item_condition, price,
        location, is_deleted, created_date, updated_date, like_count
      )
      SELECT
        gen_random_uuid(),
        g.member_id,
        'Item '||g.idx,
        'desc '||g.idx,
        (ARRAY['AVAILABLE','EXCHANGED'])[(g.idx % 2)+1],
        ((g.idx % 26) + 1),  -- int code (1..26)
        (ARRAY['SEALED','SLIGHTLY_USED','MODERATELY_USED','HEAVILY_USED'])[(g.idx % 4)+1],
        1000 * (g.idx + 1),
        ST_SetSRID(ST_MakePoint(126.7150, 37.5610), 4326),
        false, now(), now(), 0
      FROM g
    """)
          .setParameter("n_users", NUM_USERS)
          .setParameter("items_per_user", ITEMS_PER_USER)
          .setParameter("total_items", TOTAL_ITEMS)
          .executeUpdate();

      // 4) 이미지 대량 생성 (image_url 유니크 충족)
      em.createNativeQuery("""
      WITH it AS (
        SELECT item_id, row_number() OVER (ORDER BY created_date, item_id) - 1 AS rn
        FROM item
        ORDER BY created_date, item_id
        LIMIT :total_items
      ),
      g AS (
        SELECT it.item_id, gs AS k
        FROM it
        JOIN generate_series(0, :imgs_per_item - 1) AS gs ON TRUE
      )
      INSERT INTO item_image (
        item_image_id, item_item_id, image_url, file_path, created_date, updated_date
      )
      SELECT gen_random_uuid(),
             g.item_id,
             'http://example.com/img_'||g.item_id||'_'||g.k||'.jpg',
             '/dev/null',
             now(), now()
      FROM g
    """)
          .setParameter("total_items", TOTAL_ITEMS)
          .setParameter("imgs_per_item", IMAGES_PER_ITEM)
          .executeUpdate();

      // 5) Mongo 태그 벌크
      @SuppressWarnings("unchecked")
      List<UUID> itemIds = em.createNativeQuery("""
      SELECT item_id FROM item
      ORDER BY created_date, item_id
      LIMIT :lim
    """)
          .setParameter("lim", TOTAL_ITEMS)
          .getResultList();

      if (!itemIds.isEmpty()) {
        final int BATCH = 1_000; // 1천부터 시작해도 충분
        List<ItemCustomTags> buf = new ArrayList<>(BATCH);

        for (int i = 0; i < itemIds.size(); i++) {
          UUID id = itemIds.get(i);
          buf.add(ItemCustomTags.builder()
              .itemId(id)
              .customTags(List.of("태그A","태그B","ownerGroup:" + (i / ITEMS_PER_USER)))
              .build());

          if (buf.size() == BATCH) {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ItemCustomTags.class)
                .insert(buf)
                .execute();           // ★ 여기서 바로 전송/비움
            buf.clear();
          }
        }
        if (!buf.isEmpty()) {
          mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ItemCustomTags.class)
              .insert(buf)
              .execute();               // ★ 마지막 잔량도 즉시 전송
        }
      }


      // 6) 테스트에서 사용할 멤버 2명 선택 (첫/두 번째)
      @SuppressWarnings("unchecked")
      List<UUID> twoIds = em.createNativeQuery("""
      SELECT member_id
      FROM member
      ORDER BY email
      LIMIT 2
    """).getResultList();

      if (twoIds.size() >= 2) {
        testMember  = em.getReference(Member.class, twoIds.get(0));
        otherMember = em.getReference(Member.class, twoIds.get(1));
      } else {
        throw new IllegalStateException("멤버가 충분히 생성되지 않았습니다.");
      }

      return (System.nanoTime() - t0) / 1_000_000;
    });

    log.info("[BeforeAll] 준비시간(generate_series): {} ms", reservingTimeMs);
  }


  @Test
  @Order(1)
  @DisplayName("내 물품 찾기: Old vs FetchJoin vs member query + assembler vs FetchJoin + assembler 성능 비교/P95")
  void compareGetMyItems_fairBenchmark() {
    // ===== 설정 =====
    final int WARMUP_EACH = 5;     // 각 후보 사전 워밍업 호출 수
    final int ROUNDS = 40;          // 라운드 수(라운드마다 후보 실행 순서 무작위)
    final int BATCH_PER_ROUND = 3; // 한 라운드 내 각 후보 호출 횟수(배치)

    // given
    ItemRequest req = new ItemRequest();
    req.setMember(testMember);
    req.setItemStatus(ItemStatus.AVAILABLE);
    req.setPageNumber(0);
    req.setPageSize(20);

    // ===== (1) 정합성 체크: 측정 바깥에서 1회만 =====
    //ItemResponse oldRes = itemService.getMyItemsOldMethod(req);
    //ItemResponse fjRes  = itemService.getMyItemsFetchJoinMember(req);
    ItemResponse newRes = itemService.getMyItemsFetchJoinMemberDesc(req);
    ItemResponse fjAsb  = itemService.getMyItemsFetchJoinMemberDesc(req);

    assertThat(newRes.getItemDetailPage().getTotalElements())
        .isEqualTo(fjAsb.getItemDetailPage().getTotalElements());

    assertThat(newRes.getItemDetailPage().getSize())
        .isEqualTo(fjAsb.getItemDetailPage().getSize());

    // ===== (2) 워밍업: 측정 제외 =====
    //warmUp(() -> blackhole(itemService.getMyItemsOldMethod(req)), WARMUP_EACH);
    //warmUp(() -> blackhole(itemService.getMyItemsFetchJoinMember(req)), WARMUP_EACH);
    warmUp(() -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req)), WARMUP_EACH);
    warmUp(() -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req)), WARMUP_EACH);

    // ===== (3) 후보 정의 =====
    List<Candidate> candidates = new ArrayList<>(List.of(
        //new Candidate("OldMethod",           () -> blackhole(itemService.getMyItemsOldMethod(req))),
        //new Candidate("FetchJoinMember",     () -> blackhole(itemService.getMyItemsFetchJoinMember(req))),
        new Candidate("MemberQuery+Assemble",() -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req))),
        new Candidate("FetchJoin+Assemble",  () -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req)))
    ));

    Map<String, List<Long>> samples = new LinkedHashMap<>();
    candidates.forEach(c -> samples.put(c.name, new ArrayList<>()));

    // ===== (4) 본 측정: 라운드마다 순서 셔플 + 배치 호출 =====
    for (int r = 0; r < ROUNDS; r++) {
      Collections.shuffle(candidates);
      for (Candidate c : candidates) {
        long elapsed = measureBatchMs(c.action, BATCH_PER_ROUND);
        samples.get(c.name).add(elapsed);
      }
    }

    // ===== (5) 통계(중앙값/P95) 출력 =====
    log.info("========== [내 물품 찾기 - 공정 측정 결과] ==========");
    for (Map.Entry<String, List<Long>> e : samples.entrySet()) {
      List<Long> s = e.getValue();
      long p50 = percentileMs(s, 50);
      long p95 = percentileMs(s, 95);
      long p99 = percentileMs(s, 99);
      long avg = (long) s.stream().mapToLong(Long::longValue).average().orElse(0);
      log.info("{} -> rounds: {}, batch:{}; avg={} ms, p50={} ms, p95={} ms, p99= {} ms",
          e.getKey(), s.size(), BATCH_PER_ROUND, avg, p50, p95, p99);
    }
  }

  /* ===== 유틸 ===== */

  private static class Candidate {
    final String name;
    final Runnable action;
    Candidate(String name, Runnable action) { this.name = name; this.action = action; }
  }

  private void warmUp(Runnable r, int n) {
    for (int i = 0; i < n; i++) r.run();
  }

  private long measureBatchMs(Runnable r, int n) {
    long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) r.run();
    return (System.nanoTime() - t0) / 1_000_000;
  }

  /** 결과를 소비해서 JIT이 호출을 제거하지 않도록 하는 블랙홀 */
  private int blackhole(ItemResponse res) {
    // 응답 일부를 사용해 최적화 제거 방지
    int sink = res.getItemDetailPage().getSize();
    if (res.getItemDetailPage().hasContent()) {
      sink += Objects.hashCode(res.getItemDetailPage().getContent().get(0).getItemId());
    }
    return sink;
  }

  private long percentileMs(List<Long> samples, int p) {
    if (samples.isEmpty()) return 0;
    List<Long> copy = new ArrayList<>(samples);
    Collections.sort(copy);
    int idx = (int) Math.ceil((p / 100.0) * copy.size()) - 1;
    idx = Math.max(0, Math.min(idx, copy.size() - 1));
    return copy.get(idx);
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
      //for (int i = 0; i < REPEAT_COUNT; i++) sink += itemService.getItemListOld(req).getItemDetailPage().getSize();
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

  private long timeMs(Runnable r) {
    long t0 = System.nanoTime();
    r.run();
    return (System.nanoTime() - t0) / 1_000_000;
  }
}