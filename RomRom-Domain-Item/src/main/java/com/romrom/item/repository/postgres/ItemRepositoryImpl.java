package com.romrom.item.repository.postgres;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.romrom.ai.EmbeddingUtil;
import com.romrom.common.constant.ItemSortField;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.QEmbedding;
import com.romrom.common.util.QueryDslUtil;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.item.entity.postgres.Item;
import com.romrom.member.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.romrom.item.entity.postgres.QItem;
import com.romrom.member.entity.QMember;
import java.util.UUID;
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

  @Override
  public Page<Item> findAllByMemberAndItemStatusWithMember(
      Member member,
      ItemStatus status,
      Pageable pageable
  ) {
    BooleanExpression where = QueryDslUtil.allOf(
        QueryDslUtil.eqIfNotNull(ITEM.member, member),
        QueryDslUtil.eqIfNotNull(ITEM.itemStatus,status)
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
      ItemSortField sortField,
      Pageable pageable
  ) {
    BooleanExpression where = QueryDslUtil.allOf(
        QueryDslUtil.neIfNotNull(ITEM.member.memberId, memberId),
        ITEM.isDeleted.isFalse());

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

    switch (sortField) {

      case DISTANCE: {
        // radiusInMeters(미터) 안에 있는 것만 필터링
        BooleanExpression within = Expressions.booleanTemplate(
            "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326)) <= {3}",
            ITEM.location, longitude, latitude, radiusInMeters
        );

        // 정렬용 거리 표현식 (미터)
        NumberExpression<Double> distanceExpr = Expressions.numberTemplate(
            Double.class,
            "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326))",
            ITEM.location, longitude, latitude
        );

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
    }

    return QueryDslUtil.fetchPage(content, count, pageable);
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
      sql.append("AND i.created_date < :endDate ");
      countSql.append("AND i.created_date < :endDate ");
      dataParams.put("endDate", endDate.plusDays(1)); // 종료일 포함하기 위해
      countParams.put("endDate", endDate.plusDays(1));
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

    return new PageImpl<>(items, pageable, total);
  }
}
