package com.romrom.application.service;

import static com.romrom.auth.jwt.JwtUtil.REFRESH_KEY_PREFIX;
import static me.suhsaechan.suhlogger.util.SuhLogger.lineLog;
import static me.suhsaechan.suhlogger.util.SuhLogger.superLogDebug;

import com.github.javafaker.Faker;
import com.romrom.ai.service.EmbeddingService;
import com.romrom.auth.dto.CustomUserDetails;
import com.romrom.auth.jwt.JwtUtil;
import com.romrom.common.constant.*;
import com.romrom.item.dto.ItemRequest;
import com.romrom.item.dto.ItemResponse;
import com.romrom.item.entity.mongo.ItemCustomTags;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.repository.postgres.ItemImageRepository;
import com.romrom.item.repository.postgres.ItemRepository;
import com.romrom.item.service.ItemService;
import com.romrom.member.entity.Member;
import com.romrom.member.repository.MemberRepository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.suhsaechan.suhnicknamegenerator.core.SuhRandomKit;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestService {

  private final MemberRepository memberRepository;
  private final ItemRepository itemRepository;
  private final ItemImageRepository itemImageRepository;
  private final EmbeddingService embeddingService;
  private final JwtUtil jwtUtil;
  private final RedisTemplate<String, Object> redisTemplate;
  private final SuhRandomKit suhRandomKit = SuhRandomKit.builder().locale("ko").uuidLength(4).numberLength(4).build();
  private final Faker koFaker = new Faker(new Locale("ko", "KR"));
  private final Faker enFaker = new Faker(new Locale("en"));
  private static final String MOCK_IMAGE_URL = "https://picsum.photos/300/400";
  private static final GeometryFactory GF =
      new GeometryFactory(new PrecisionModel(), 4326);

  private final ItemService itemService;
  private final MongoTemplate mongoTemplate;
  private final PlatformTransactionManager txManager;

  @PersistenceContext
  private EntityManager em;

  /**
   * 회원 이메일로 가짜 로그인 처리 회원이 없으면 신규 가입 후, isFirstLogin 설정
   */
  @Transactional

  public TestResponse testSignIn(TestRequest request) {
    boolean isFirstLogin = false;
    String email = request.getEmail();

    // 이메일에 해당하는 회원 조회, 없으면 신규 가입 처리
    Member member = memberRepository.findByEmail(email)
        .orElseGet(() -> {

          Member newMember = Member.builder()
              .email(email)
              .socialPlatform(request.getSocialPlatform())
              .nickname(suhRandomKit.nicknameWithNumber())
              .profileUrl("TEST")
              .role(Role.ROLE_USER)
              .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
              .isFirstItemPosted(false)
              .build();

          Member savedMember = memberRepository.save(newMember);

          // 신규 가입시 첫 로그인으로 설정
          savedMember.setIsFirstLogin(true);
          return savedMember;
        });

    // JWT 토큰 생성
    CustomUserDetails customUserDetails = new CustomUserDetails(member);
    String accessToken = jwtUtil.createAccessToken(customUserDetails);
    String refreshToken = jwtUtil.createRefreshToken(customUserDetails);

    // RefreshToken -> Redis 저장 (키: "RT:{memberId}")
    redisTemplate.opsForValue().set(
        REFRESH_KEY_PREFIX + customUserDetails.getMemberId(),
        refreshToken,
        jwtUtil.getRefreshExpirationTime(),
        TimeUnit.MILLISECONDS
    );

    lineLog("가짜 로그인 성공: email=" + email);
    superLogDebug(member);

    return TestResponse.builder()
        .member(member)
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .isFirstLogin(member.getIsFirstLogin())
        .isFirstItemPosted(member.getIsFirstItemPosted())
        .build();
  }

  // count 만큼 Mock 사용자 생성
  @Transactional
  public void createMockMembers(Integer count) {
    List<Member> mockMembers = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      try {
        mockMembers.add(createMockMember());
      } catch (Exception e) {
        log.error("Mock 사용자 생성 실패: {}", e.getMessage());
      }
    }
    log.debug("Mock 사용자 {}명 생성 완료", mockMembers.size());
  }

  /**
   * Mock Member 생성 DataFaker 로 이메일, 닉네임, 프로필 URL, 소셜 플랫폼, 역할, 계정 상태를 생성
   *
   * @return 생성된 Member 객체
   */
  @Transactional
  public Member createMockMember() {
    Member mockMember = Member.builder()
        .email(enFaker.internet().emailAddress())
        .nickname(suhRandomKit.nicknameWithNumber())
        .profileUrl(MOCK_IMAGE_URL)
        .socialPlatform(enFaker.options().option(SocialPlatform.class))
        .role(Role.ROLE_USER)
        .accountStatus(AccountStatus.ACTIVE_ACCOUNT)
        .build();

    // Mock Member 저장
    memberRepository.save(mockMember);
    return mockMember;
  }

  // count 만큼 Mock 물품 생성
  @Transactional
  public void createMockItems(Integer count) {
    List<Item> mockItems = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      try {
        mockItems.add(createMockItem());
      } catch (Exception e) {
        log.error("Mock 물품 생성 실패: index={}, error={}", i, e.getMessage(), e);
      }
    }
    log.debug("Mock 물품 {}개 생성 완료", mockItems.size());
  }

  /**
   * Mock Item 생성 DataFaker 로 물품명, 물품 설명, 카테고리, 상태, 거래 옵션을 생성
   *
   * @return 생성된 Item 객체
   */
  @Transactional
  public Item createMockItem() {
    Member mockMember = createMockMember();

    List<ItemTradeOption> tradeOptions = Stream.generate(() -> enFaker.options().option(ItemTradeOption.class))
        .distinct()
        .limit(enFaker.number().numberBetween(1, 3))
        .toList();

    Item mockItem = Item.builder()
        .member(mockMember)
        .itemName(koFaker.commerce().productName())
        .itemDescription(koFaker.lorem().sentence())
        .itemCategory(enFaker.options().option(ItemCategory.class))
        .itemCondition(enFaker.options().option(ItemCondition.class))
        .itemTradeOptions(tradeOptions)
        .location(createMockLocation())
        .likeCount(enFaker.number().numberBetween(0, 100))
        .price(enFaker.number().numberBetween(10, 1001) * 100)
        .build();

    // Mock Item 저장
    itemRepository.save(mockItem);
    mockMember.setIsFirstItemPosted(true);

    // 아이템 임베딩 생성 및 저장
    try {
      embeddingService.generateAndSaveItemEmbedding(extractItemText(mockItem), mockItem.getItemId());
      log.debug("Mock 아이템 임베딩 생성 완료: itemId={}", mockItem.getItemId());
    } catch (Exception e) {
      log.error("Mock 아이템 임베딩 생성 실패: itemId={}", mockItem.getItemId(), e);
    }

    // 1~10개 사이의 Mock ItemImage 생성
    createMockItemImages(mockItem, enFaker.number().numberBetween(1, 11));

    return mockItem;
  }

  /**
   * Mock ItemImage 생성 DataFaker 로 이미지 URL, 파일 경로, 원본 파일명, 업로드된 파일명, 파일 크기를 생성
   *
   * @param item  Item 객체
   * @param count 생성할 Mock ItemImage 개수
   */
  @Transactional
  public void createMockItemImages(Item item, int count) {
    List<ItemImage> mockItemImages = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ItemImage mockItemImage = ItemImage.builder()
          .item(item)
          .filePath(null)
          .imageUrl("https://picsum.photos/300/400?random=" + enFaker.number().randomNumber())
          .build();
      mockItemImages.add(mockItemImage);

      // Mock ItemImage 저장
      itemImageRepository.save(mockItemImage);
    }
    log.debug("Mock ItemImage {}개 생성 완료: itemId={}", mockItemImages.size(), item.getItemId());
  }

  /* --------------------- 시드/준비 --------------------- */

  @Data @AllArgsConstructor @NoArgsConstructor
  public static class SeedResult {
    private long reservingTimeMs;
    private UUID testMemberId;
    private UUID otherMemberId;
    private long totalUsers;
    private long totalItems;
    private long totalImages;
  }
  public SeedResult seedAndPrepare(int numUsers, int itemsPerUser, int imagesPerItem, int mongoBatch) {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    long reservingTimeMs = tx.execute(status -> {
      long t0 = System.nanoTime();

      // 0) Mongo 초기화
      mongoTemplate.dropCollection(ItemCustomTags.class);

      // 1) RDB 초기화
      em.createNativeQuery("""
        TRUNCATE TABLE item_image, item, member_item_category, member_location, member
        RESTART IDENTITY CASCADE
      """).executeUpdate();

      // 1-1) 확장 모듈 보장
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pgcrypto").executeUpdate();
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS postgis").executeUpdate();

      // 1-2) 과거 남은 CHECK 제약 회피(테스트 DB 한정)
      em.createNativeQuery("ALTER TABLE IF EXISTS item DROP CONSTRAINT IF EXISTS item_item_status_check").executeUpdate();
      em.createNativeQuery("ALTER TABLE IF EXISTS item DROP CONSTRAINT IF EXISTS item_item_condition_check").executeUpdate();

      // 1-3) write latency 줄이기(트랜잭션 로컬)
      em.createNativeQuery("SET LOCAL synchronous_commit = 'off'").executeUpdate();

      final int TOTAL_ITEMS = numUsers * itemsPerUser;

      // 2) 멤버 대량 생성
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
      """).setParameter("n_users", numUsers).executeUpdate();

      // 3) 아이템 대량 생성
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
          ((g.idx % 26) + 1),
          (ARRAY['SEALED','SLIGHTLY_USED','MODERATELY_USED','HEAVILY_USED'])[(g.idx % 4)+1],
          1000 * (g.idx + 1),
          ST_SetSRID(ST_MakePoint(126.7150, 37.5610), 4326),
          false, now(), now(), 0
        FROM g
      """)
          .setParameter("n_users", numUsers)
          .setParameter("items_per_user", itemsPerUser)
          .setParameter("total_items", TOTAL_ITEMS)
          .executeUpdate();

      // 4) 이미지 대량 생성
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
          .setParameter("imgs_per_item", imagesPerItem)
          .executeUpdate();

      // 5) Mongo 태그 벌크 (★ 배치마다 execute)
      @SuppressWarnings("unchecked")
      List<UUID> itemIds = em.createNativeQuery("""
        SELECT item_id FROM item
        ORDER BY created_date, item_id
        LIMIT :lim
      """).setParameter("lim", TOTAL_ITEMS).getResultList();

      if (!itemIds.isEmpty()) {
        final int BATCH = Math.max(100, mongoBatch);
        List<ItemCustomTags> buf = new ArrayList<>(BATCH);
        for (int i = 0; i < itemIds.size(); i++) {
          UUID id = itemIds.get(i);
          buf.add(ItemCustomTags.builder()
              .itemId(id)
              .customTags(List.of("태그A","태그B","ownerGroup:" + (i / itemsPerUser)))
              .build());
          if (buf.size() == BATCH) {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ItemCustomTags.class)
                .insert(buf)
                .execute();
            buf.clear();
          }
        }
        if (!buf.isEmpty()) {
          mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ItemCustomTags.class)
              .insert(buf)
              .execute();
        }
      }

      return (System.nanoTime() - t0) / 1_000_000;
    });

    // 벤치에 쓸 멤버 2명 선택
    @SuppressWarnings("unchecked")
    List<UUID> twoIds = em.createNativeQuery("""
      SELECT member_id FROM member
      ORDER BY email
      LIMIT 2
    """).getResultList();

    if (twoIds.size() < 2) throw new IllegalStateException("멤버가 충분히 생성되지 않았습니다.");

    return new SeedResult(
        reservingTimeMs,
        twoIds.get(0),
        twoIds.get(1),
        count("member"),
        count("item"),
        count("item_image")
    );
  }

  private long count(String table) {
    Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult();
    return n.longValue();
  }

  @Data @AllArgsConstructor @NoArgsConstructor
  public static class Stat {
    private long avgMs;
    private long p50Ms;
    private long p95Ms;
    private long p99Ms;
    private int rounds;
    private int batchPerRound;
    private int samples; // rounds 개수
  }

  @Data @AllArgsConstructor @NoArgsConstructor
  public static class MyItemsBenchResult {
    private Map<String, Stat> results;
  }

  public MyItemsBenchResult benchMyItems(UUID testMemberId,
                                         ItemStatus itemStatus,
                                         int pageNumber, int pageSize,
                                         int warmupEach, int rounds, int batchPerRound) {

    ItemRequest req = new ItemRequest();
    req.setMember(em.getReference(Member.class, testMemberId));
    req.setItemStatus(itemStatus);
    req.setPageNumber(pageNumber);
    req.setPageSize(pageSize);

    // 정합성(1회)
    ItemResponse newRes = itemService.getMyItemsWithMemberQuery(req);
    ItemResponse fjAsb  = itemService.getMyItemsFetchJoinMemberDesc(req);
    if (newRes.getItemDetailPage().getTotalElements() != fjAsb.getItemDetailPage().getTotalElements()) {
      throw new IllegalStateException("정합성 불일치: totalElements 다름");
    }
    if (newRes.getItemDetailPage().getSize() != fjAsb.getItemDetailPage().getSize()) {
      throw new IllegalStateException("정합성 불일치: page size 다름");
    }

    // 워밍업
    warmUp(() -> blackhole(itemService.getMyItemsWithMemberQuery(req)), warmupEach);
    warmUp(() -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req)), warmupEach);

    // 후보
    List<Candidate> candidates = new ArrayList<>(List.of(
        new Candidate("MemberQuery+Assemble", () -> blackhole(itemService.getMyItemsWithMemberQuery(req))),
        new Candidate("FetchJoin+Assemble",   () -> blackhole(itemService.getMyItemsFetchJoinMemberDesc(req)))
    ));
    Map<String, List<Long>> samples = new LinkedHashMap<>();
    candidates.forEach(c -> samples.put(c.name, new ArrayList<>()));

    // 본 측정
    for (int r = 0; r < rounds; r++) {
      Collections.shuffle(candidates);
      for (Candidate c : candidates) {
        long elapsed = measureBatchMs(c.action, batchPerRound);
        samples.get(c.name).add(elapsed);
      }
    }

    Map<String, Stat> out = new LinkedHashMap<>();
    for (Map.Entry<String, List<Long>> e : samples.entrySet()) {
      List<Long> s = e.getValue();
      long p50 = percentileMs(s, 50);
      long p95 = percentileMs(s, 95);
      long p99 = percentileMs(s, 99);
      long avg = (long) s.stream().mapToLong(Long::longValue).average().orElse(0);
      out.put(e.getKey(), new Stat(avg, p50, p95, p99, rounds, batchPerRound, s.size()));
    }
    return new MyItemsBenchResult(out);
  }
  /* --------------------- 유틸 --------------------- */

  @AllArgsConstructor private static class Candidate {
    final String name; final Runnable action;
  }

  private void warmUp(Runnable r, int n) { for (int i = 0; i < n; i++) r.run(); }

  private long measureBatchMs(Runnable r, int n) {
    long t0 = System.nanoTime();
    for (int i = 0; i < n; i++) r.run();
    return (System.nanoTime() - t0) / 1_000_000;
  }

  private int blackhole(ItemResponse res) {
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

  @Data @AllArgsConstructor @NoArgsConstructor
  public static class ResetResult {
    private long elapsedMs;
    private long members;
    private long items;
    private long images;
    private long mongoDocs;
  }

  /** 모든 테스트 데이터 리셋: Mongo 컬렉션 삭제 + PG 테이블 TRUNCATE + 시퀀스 리셋 */
  public ResetResult reset() {
    TransactionTemplate tx = new TransactionTemplate(txManager);

    long elapsedMs = tx.execute(status -> {
      long t0 = System.nanoTime();

      // Mongo 초기화
      mongoTemplate.dropCollection(ItemCustomTags.class);

      // RDB 초기화
      em.createNativeQuery("""
        TRUNCATE TABLE item_image, item, member_item_category, member_location, member
        RESTART IDENTITY CASCADE
      """).executeUpdate();

      // 확장 모듈 보장(테스트 DB에서 드물게 빠져있는 경우 대비)
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS pgcrypto").executeUpdate();
      em.createNativeQuery("CREATE EXTENSION IF NOT EXISTS postgis").executeUpdate();

      return (System.nanoTime() - t0) / 1_000_000;
    });

    // 리셋 후 카운트(검증용)
    long members = count("member");
    long items   = count("item");
    long images  = count("item_image");
    long mongoDocs = 0L; // dropCollection 했으므로 0

    return new ResetResult(elapsedMs, members, items, images, mongoDocs);
  }

  private Point createMockLocation() {
    double longitude = Double.parseDouble(enFaker.address().longitude());
    double latitude = Double.parseDouble(enFaker.address().latitude());
    return GF.createPoint(new Coordinate(longitude, latitude));
  }

  private String extractItemText(Item item) {
    return item.getItemName() + ", " + item.getItemDescription();
  }
}
