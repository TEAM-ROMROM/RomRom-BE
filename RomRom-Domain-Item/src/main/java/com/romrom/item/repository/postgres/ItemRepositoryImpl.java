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
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.QItem;
import com.romrom.member.entity.Member;
import com.romrom.member.entity.QMember;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<Item> findAllByMemberAndItemStatusWithMember(
      Member member,
      ItemStatus status,
      Pageable pageable
  ) {
    QItem item = QItem.item;
    QMember qMember = QMember.member;

    BooleanExpression where = QueryDslUtil.allOf(
        item.member.eq(member),
        item.itemStatus.eq(status)
    );

    JPAQuery<Item> contentQuery = queryFactory
        .selectFrom(item)
        .join(item.member, qMember).fetchJoin()
        .where(where);

    JPAQuery<Long> countQuery = queryFactory
        .select(item.count())
        .from(item)
        .where(where);

    QueryDslUtil.applySorting(contentQuery, pageable, Item.class, item.getMetadata().getName());

    return QueryDslUtil.fetchPage(contentQuery, countQuery, pageable);
  }

  @Override
  public Page<Item> filterItemsFetchJoinMember(
      UUID memberId,
      Pageable pageable
  ) {
    QItem item = QItem.item;
    QMember member = QMember.member;

    BooleanExpression where = item.member.memberId.ne(memberId);

    JPAQuery<Item> contentQuery = queryFactory
        .selectFrom(item)
        .join(item.member, member).fetchJoin()
        .where(where);

    JPAQuery<Long> countQuery = queryFactory
        .select(item.count())
        .from(item)
        .where(where);

    QueryDslUtil.applySorting(contentQuery, pageable, Item.class, item.getMetadata().getName());

    return QueryDslUtil.fetchPage(contentQuery, countQuery, pageable);
  }

  @Override
  public Page<Item> filterItems(
      UUID memberId,
      Double longitude,
      Double latitude,
      Double radius,
      float[] memberEmbedding,
      ItemSortField sortField,
      Pageable pageable
  ) {
    QItem item = QItem.item;
    QMember member = QMember.member;
    QEmbedding embedding = QEmbedding.embedding1;

    BooleanExpression where = QueryDslUtil.allOf(
        item.member.memberId.ne(memberId),
        item.isDeleted.isFalse());

    JPAQuery<Item> content = queryFactory
        .selectFrom(item)
        .join(item.member, member).fetchJoin()
        .where(where);

    JPAQuery<Long> count = queryFactory
        .select(item.count())
        .from(item)
        .where(where);

    Sort.Direction dir =
        pageable.getSort().isEmpty() ? Sort.Direction.DESC
            : pageable.getSort().iterator().next().getDirection();

    switch (sortField) {

      case DISTANCE: {
        // radius(미터) 안에 있는 것만 필터링
        BooleanExpression within = Expressions.booleanTemplate(
            "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326)) <= {3}",
            item.location, longitude, latitude, radius
        );

        // 정렬용 거리 표현식 (미터)
        NumberExpression<Double> distanceExpr = Expressions.numberTemplate(
            Double.class,
            "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326))",
            item.location, longitude, latitude
        );

        content.where(within);
        count.where(within);

        content.orderBy(
            new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, distanceExpr),
            new OrderSpecifier<>(Order.DESC, item.createdDate)
        );
        break;
      }

      case PREFERRED_CATEGORY: {
        content.join(embedding)
            .on(embedding.originalId.eq(item.itemId)
                .and(embedding.originalType.eq(OriginalType.ITEM)));

        count.join(embedding)
            .on(embedding.originalId.eq(item.itemId)
                .and(embedding.originalType.eq(OriginalType.ITEM)));

        String vectorLiteral = EmbeddingUtil.toVectorLiteral(memberEmbedding);

        NumberExpression<Double> simExpr = Expressions.numberTemplate(
            Double.class,
            "function('cosine_distance', {0}, cast('{1}' as vector))",
            embedding.embedding,
            Expressions.stringTemplate(vectorLiteral)
        );

        content.orderBy(
            new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, simExpr),
            new OrderSpecifier<>(Order.DESC, item.createdDate)
        );

        break;
      }

      case CREATED_DATE: {

        content.orderBy(new OrderSpecifier<>(dir.isAscending() ? Order.ASC : Order.DESC, item.createdDate));
        break;
      }
    }

    return QueryDslUtil.fetchPage(content, count, pageable);
  }

}
