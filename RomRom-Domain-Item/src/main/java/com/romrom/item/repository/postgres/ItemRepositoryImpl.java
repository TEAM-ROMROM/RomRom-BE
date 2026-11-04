package com.romrom.item.repository.postgres;

import static java.time.Instant.now;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.QEmbedding;
import com.romrom.common.util.QueryDslUtil;
import com.romrom.item.dto.HomeFeedVarietyOption;
import com.romrom.item.config.RecommendationConfig;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.ItemImage;
import com.romrom.item.entity.postgres.QItem;
import com.romrom.item.entity.postgres.QItemImage;
import com.romrom.item.entity.postgres.UserInteractionScore;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.QMember;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

  private static final QMember MEMBER = QMember.member;
  private static final QItem ITEM = QItem.item;
  private static final QEmbedding EMBEDDING = QEmbedding.embedding1;

  private final EntityManager entityManager;
  private final JPAQueryFactory queryFactory;
  private final RecommendationConfig recommendationConfig;

  @Override
  public Page<Item> findAllByMemberAndItemStatusAndIsDeletedFalseWithMember(
      Member member,
      ItemStatus status,
      Pageable pageable
  ) {
    BooleanExpression where = QueryDslUtil.allOf(
        QueryDslUtil.eqIfNotNull(ITEM.member, member),
        QueryDslUtil.eqIfNotNull(ITEM.itemStatus,status),
        ITEM.isDeleted.isFalse()
    );

    JPAQuery<Item> contentQuery = queryFactory
        .selectFrom(ITEM)
        .join(ITEM.member, MEMBER).fetchJoin()
        .where(where);

    JPAQuery<Long> countQuery = queryFactory
        .select(ITEM.count())
        .from(ITEM)
        .where(where);

    QueryDslUtil.applySorting(contentQuery, pageable, Item.class, ITEM.getMetadata().getName());

    return QueryDslUtil.fetchPage(contentQuery, countQuery, pageable);
  }

  @Override
  public Page<Item> filterItemsFetchJoinMember(
      UUID memberId,
      Pageable pageable
  ) {
    BooleanExpression where = QueryDslUtil.neIfNotNull(ITEM.member.memberId, memberId);

    JPAQuery<Item> contentQuery = queryFactory
        .selectFrom(ITEM)
        .join(ITEM.member, MEMBER).fetchJoin()
        .where(where);

    JPAQuery<Long> countQuery = queryFactory
        .select(ITEM.count())
        .from(ITEM)
        .where(where);

    QueryDslUtil.applySorting(contentQuery, pageable, Item.class, ITEM.getMetadata().getName());

    return QueryDslUtil.fetchPage(contentQuery, countQuery, pageable);
  }

  @Override
  public Page<Item> filterItems(
      UUID memberId,
      Double longitude,
      Double latitude,
      Double radiusInMeters,
      float[] memberEmbedding,
      List<UserInteractionScore> userInteractionScores,
      List<ItemCategory> preferredCategories,
      ItemSortField sortField,
      HomeFeedVarietyOption homeFeedVarietyOption,
      Pageable pageable
  ) {
    BooleanExpression where = ItemFeedQueryExpressions.buildFeedVisibilityCondition(memberId);

    JPAQuery<Item> content = queryFactory
        .selectFrom(ITEM)
        .join(ITEM.member, MEMBER).fetchJoin()
        .where(where);

    JPAQuery<Long> count = queryFactory
        .select(ITEM.count())
        .from(ITEM)
        .where(where);

    Sort.Direction dir =
        pageable.getSort().isEmpty() ? Sort.Direction.DESC
            : pageable.getSort().iterator().next().getDirection();

    // 최근 본 물품 뒤로 (정렬 1순위, 모든 정렬 기준 공통, 걸러내지 않아 피드가 비지 않음) (#966)
    if (homeFeedVarietyOption.hasRecentlySeenItems()) {
      content.orderBy(ItemFeedQueryExpressions.buildRecentlySeenLastOrder(homeFeedVarietyOption.recentlySeenItemIds()));
    }

    switch (sortField) {

      case DISTANCE: {
        // radiusInMeters(미터) 안에 있는 것만 필터링
        BooleanExpression within =
            ItemFeedQueryExpressions.buildWithinRadiusCondition(longitude, latitude, radiusInMeters);

        // 정렬용 거리 표현식 (미터)
        NumberExpression<Double> distanceExpr = ItemFeedQueryExpressions.buildDistanceExpression(longitude, latitude);

        content.where(within);
        count.where(within);

        content.orderBy(
            new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, distanceExpr),
            new OrderSpecifier<>(Order.DESC, ITEM.createdDate)
        );
        break;
      }

      case PREFERRED_CATEGORY: {
        content.join(EMBEDDING)
            .on(EMBEDDING.originalId.eq(ITEM.itemId)
                .and(EMBEDDING.originalType.eq(OriginalType.ITEM)));

        count.join(EMBEDDING)
            .on(EMBEDDING.originalId.eq(ITEM.itemId)
                .and(EMBEDDING.originalType.eq(OriginalType.ITEM)));

        String vectorLiteral = EmbeddingUtil.toVectorLiteral(memberEmbedding);

        NumberExpression<Double> simExpr = Expressions.numberTemplate(
            Double.class,
            "function('cosine_distance', {0}, cast('{1}' as vector))",
            EMBEDDING.embedding,
            Expressions.stringTemplate(vectorLiteral)
        );

        content.orderBy(
            new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, simExpr),
            new OrderSpecifier<>(Order.DESC, ITEM.createdDate)
        );

        break;
      }

      case CREATED_DATE: {
        content.orderBy(new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, ITEM.createdDate));
        break;
      }

      case RECOMMENDED: {
        if (longitude != null && latitude != null && radiusInMeters != null) {
          BooleanExpression within =
              ItemFeedQueryExpressions.buildWithinRadiusCondition(longitude, latitude, radiusInMeters);
          content.where(within);
          count.where(within);
        }

        NumberExpression<Double> recommendedScoreExpr = buildRecommendedScoreExpression(
            memberId,
            userInteractionScores,
            preferredCategories
        );
        // 시드 있으면 물품별 고정 가산점 추가 > 비슷한 점수끼리 순서 섞임 (같은 시드면 페이지 간 순서 동일) (#966)
        if (homeFeedVarietyOption.hasFeedSeed()) {
          recommendedScoreExpr = recommendedScoreExpr.add(buildSeedShuffleScoreExpression(homeFeedVarietyOption.feedSeed()));
        }

        content.orderBy(
            new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, recommendedScoreExpr),
            new OrderSpecifier<>(Order.DESC, ITEM.createdDate)
        );
        break;
      }

      default: {
        content.orderBy(new OrderSpecifier<>(Order.DESC, ITEM.createdDate));
        break;
      }
    }

    return QueryDslUtil.fetchPage(content, count, pageable);
  }

  @Override
  public List<Item> findPromotedFeedCandidates(
      UUID viewerMemberId,
      double longitude,
      double latitude,
      double radiusInMeters,
      List<ItemCategory> preferredCategories,
      LocalDateTime now,
      int limit
  ) {
    // 피드와 같은 반경 조건 (filterItems DISTANCE·RECOMMENDED와 동일 식)
    BooleanExpression withinSearchRadius =
        ItemFeedQueryExpressions.buildWithinRadiusCondition(longitude, latitude, radiusInMeters);

    BooleanExpression where = QueryDslUtil.allOf(
        ItemFeedQueryExpressions.buildFeedVisibilityCondition(viewerMemberId),
        ITEM.promotionExpiresAt.after(now),
        ItemFeedQueryExpressions.buildPreferredCategoryPredicate(preferredCategories),
        withinSearchRadius
    );

    return queryFactory
        .selectFrom(ITEM)
        .join(ITEM.member, MEMBER).fetchJoin()
        .where(where)
        .orderBy(ITEM.promotionExpiresAt.desc(), ITEM.itemId.asc())
        .limit(limit)
        .fetch();
  }

  @Override
  public Page<Item> findItemsForAdmin(
      String searchKeyword,
      ItemCategory itemCategory,
      ItemCondition itemCondition,
      ItemStatus itemStatus,
      Integer minPrice,
      Integer maxPrice,
      LocalDateTime startDate,
      LocalDateTime endDate,
      Pageable pageable) {

    Map<String, Object> dataParams = new HashMap<>();
    Map<String, Object> countParams = new HashMap<>();

    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT i.* FROM item i 
        LEFT JOIN member m ON i.member_member_id = m.member_id 
        WHERE i.is_deleted = false 
        """);

    StringBuilder countSql = new StringBuilder("""
        SELECT COUNT(DISTINCT i.item_id) FROM item i 
        LEFT JOIN member m ON i.member_member_id = m.member_id 
        WHERE i.is_deleted = false 
        """);

    if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
      String condition = """
          AND (LOWER(i.item_name) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) 
               OR LOWER(i.item_description) LIKE LOWER(CONCAT('%', :searchKeyword, '%')) 
               OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :searchKeyword, '%'))) 
          """;
      sql.append(condition);
      countSql.append(condition);
      dataParams.put("searchKeyword", searchKeyword.trim());
      countParams.put("searchKeyword", searchKeyword.trim());
    }

    if (itemCategory != null) {
      sql.append("AND i.item_category = :itemCategory ");
      countSql.append("AND i.item_category = :itemCategory ");
      dataParams.put("itemCategory", itemCategory.ordinal());
      countParams.put("itemCategory", itemCategory.ordinal());
    }

    if (itemCondition != null) {
      sql.append("AND i.item_condition = :itemCondition ");
      countSql.append("AND i.item_condition = :itemCondition ");
      dataParams.put("itemCondition", itemCondition.name());
      countParams.put("itemCondition", itemCondition.name());
    }

    if (itemStatus != null) {
      sql.append("AND i.item_status = :itemStatus ");
      countSql.append("AND i.item_status = :itemStatus ");
      dataParams.put("itemStatus", itemStatus.name());
      countParams.put("itemStatus", itemStatus.name());
    }

    if (minPrice != null) {
      sql.append("AND i.price >= :minPrice ");
      countSql.append("AND i.price >= :minPrice ");
      dataParams.put("minPrice", minPrice);
      countParams.put("minPrice", minPrice);
    }

    if (maxPrice != null) {
      sql.append("AND i.price <= :maxPrice ");
      countSql.append("AND i.price <= :maxPrice ");
      dataParams.put("maxPrice", maxPrice);
      countParams.put("maxPrice", maxPrice);
    }

    if (startDate != null) {
      sql.append("AND i.created_date >= :startDate ");
      countSql.append("AND i.created_date >= :startDate ");
      dataParams.put("startDate", startDate);
      countParams.put("startDate", startDate);
    }

    if (endDate != null) {
      // endDate: 종료일 23:59:59.999999999 (종료일 포함)
      sql.append("AND i.created_date <= :endDate ");
      countSql.append("AND i.created_date <= :endDate ");
      dataParams.put("endDate", endDate);
      countParams.put("endDate", endDate);
    }

    if (pageable.getSort().isSorted()) {
      pageable.getSort().forEach(order -> {
        String property = order.getProperty();
        String direction = order.isAscending() ? "ASC" : "DESC";

        switch (property) {
          case "createdDate" -> sql.append("ORDER BY i.created_date ").append(direction).append(" ");
          case "updatedDate" -> sql.append("ORDER BY i.updated_date ").append(direction).append(" ");
          case "itemName" -> sql.append("ORDER BY i.item_name ").append(direction).append(" ");
          case "price" -> sql.append("ORDER BY i.price ").append(direction).append(" ");
          case "likeCount" -> sql.append("ORDER BY i.like_count ").append(direction).append(" ");
          default -> sql.append("ORDER BY i.created_date DESC ");
        }
      });
    } else {
      sql.append("ORDER BY i.created_date DESC ");
    }

    Query dataQuery = entityManager.createNativeQuery(sql.toString(), Item.class);
    Query countQuery = entityManager.createNativeQuery(countSql.toString());

    dataParams.forEach(dataQuery::setParameter);
    countParams.forEach(countQuery::setParameter);

    dataQuery.setFirstResult((int) pageable.getOffset());
    dataQuery.setMaxResults(pageable.getPageSize());

    @SuppressWarnings("unchecked")
    List<Item> items = dataQuery.getResultList();
    Long total = ((Number) countQuery.getSingleResult()).longValue();

    // itemImages 는 OneToMany(LAZY) 라 native SQL 결과만으로는 미초기화됨.
    // OSIV=false 환경에서 응답 직렬화 시점에 lazy 로딩이 불가능하므로
    // 트랜잭션 내에서 IN 쿼리 한 번으로 일괄 페치 후 각 Item 에 주입한다.
    fetchItemImagesForAdmin(items);

    return new PageImpl<>(items, pageable, total);
  }

  /**
   * 관리자 물품 목록의 itemImages 컬렉션을 일괄 로드하여 각 Item 에 주입한다. (N+1 방지)
   */
  private void fetchItemImagesForAdmin(List<Item> items) {
    if (items == null || items.isEmpty()) {
      return;
    }

    List<UUID> itemIds = items.stream()
        .map(Item::getItemId)
        .toList();

    QItemImage itemImage = QItemImage.itemImage;
    List<ItemImage> fetchedItemImages = queryFactory
        .selectFrom(itemImage)
        .where(itemImage.item.itemId.in(itemIds))
        .fetch();

    // ItemImage.item 은 LAZY 프록시 — 식별자 접근만 하므로 추가 쿼리 발생하지 않음
    Map<UUID, List<ItemImage>> itemImagesByItemId = fetchedItemImages.stream()
        .collect(Collectors.groupingBy(image -> image.getItem().getItemId()));

    items.forEach(loadedItem ->
        loadedItem.setItemImages(
            itemImagesByItemId.getOrDefault(loadedItem.getItemId(), new ArrayList<>())
        )
    );
  }

  /**
   * 최종 추천 점수 합산
   * 카테고리 선호도 점수와 시간 감쇠 점수를 합산하여 최종 점수 산출
   */
  private NumberExpression<Double> buildRecommendedScoreExpression(
      UUID memberId, List<UserInteractionScore> userInteractionScores, List<ItemCategory> preferredCategories
  ) {
    double weightCategory = recommendationConfig.getWeight().getCategory();
    double weightFreshness = recommendationConfig.getWeight().getFreshness();
    double weightExplicit = recommendationConfig.getWeight().getExplicit();
    double weightImplicit = recommendationConfig.getWeight().getImplicit();
    double lambda = recommendationConfig.getTimeDecayLambda();

    BooleanExpression isExplicitPreferred = ItemFeedQueryExpressions.buildPreferredCategoryPredicate(preferredCategories);
    NumberExpression<Double> implicitNormalizedExpr = buildImplicitNormalizedCase(userInteractionScores);
    long nowEpochSeconds = now().getEpochSecond();

    // (카테고리취향점수 * 카테고리가중치) + (신선도점수 * 신선도가중치)
    return Expressions.numberTemplate(
        Double.class,
        "(( (case when {0} then 1.0 else 0.0 end) * {1} + ({2}) * {3} ) * {4} + " +
        "(EXP((0.0 - {5}) * (({6} - (EXTRACT(EPOCH FROM {7}))) / 86400.0)) * {8}))",
        isExplicitPreferred, Expressions.constant(weightExplicit), implicitNormalizedExpr,
        Expressions.constant(weightImplicit), Expressions.constant(weightCategory),
        Expressions.constant(lambda), Expressions.constant((double) nowEpochSeconds),
        ITEM.createdDate, Expressions.constant(weightFreshness)
    );
  }

  /**
   * 시드 섞기 가산점: hashtext(물품ID + 시드) > 0~1 균등값 > 섞기 가중치 곱 (물품·시드가 같으면 항상 같은 값)
   */
  private NumberExpression<Double> buildSeedShuffleScoreExpression(long feedSeed) {
    // mod 먼저 적용 (abs(int 최솟값) 범위 초과 방지), 0 ~ 0.9999
    // 나눗셈은 실수 리터럴 (파라미터로 넘기면 정수로 추론돼 정수 나눗셈 > 항상 0)
    NumberExpression<Double> seedShuffleUnitExpr = Expressions.numberTemplate(
        Double.class,
        "(abs(mod(cast(function('hashtext', concat(cast({0} as String), {1})) as Integer), 10000)) / 10000.0)",
        ITEM.itemId,
        Expressions.constant(":" + feedSeed)
    );
    return seedShuffleUnitExpr.multiply(recommendationConfig.getWeight().getSeedShuffle());
  }

  /**
   * 암묵적 활동 점수 정규화
   * 활동 점수가 높은 카테고리에 0~1 사이의 가산점 부여
   */
  private NumberExpression<Double> buildImplicitNormalizedCase(List<UserInteractionScore> userInteractionScores) {
    if (userInteractionScores == null || userInteractionScores.isEmpty())
      return Expressions.numberTemplate(Double.class, "0.0");
    double total = userInteractionScores.stream().mapToDouble(s -> s.getTotalScore() != null ? s.getTotalScore() : 0.0).sum();
    if (total <= 0.0)
      return Expressions.numberTemplate(Double.class, "0.0");

    CaseBuilder caseBuilder = new CaseBuilder();
    CaseBuilder.Cases<Double, NumberExpression<Double>> cases = null;
    for (UserInteractionScore score : userInteractionScores) {
      if (score.getItemCategory() == null)
        continue;
      double normalized = (score.getTotalScore() != null ? score.getTotalScore() : 0.0) / total;
      cases = (cases == null) ? caseBuilder.when(ITEM.itemCategory.eq(score.getItemCategory())).then(normalized)
          : cases.when(ITEM.itemCategory.eq(score.getItemCategory())).then(normalized);
    }
    return (cases == null) ? Expressions.numberTemplate(Double.class, "0.0") : cases.otherwise(0.0);
  }
}
