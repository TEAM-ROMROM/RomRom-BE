package com.romrom.item.repository.postgres;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.util.QueryDslUtil;
import com.romrom.item.dto.ItemSearchCondition;
import com.romrom.item.entity.postgres.Item;
import com.romrom.item.entity.postgres.QItem;
import com.romrom.member.entity.QMember;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * 물품 검색 쿼리: 피드 노출 조건 재사용 + 검색어·카테고리·제외 물품 조건, 정렬 4종
 * 검색어: lower(컬럼) like '%검색어%' (V1_4_73 pg_trgm GIN 인덱스가 있으면 3자 이상 검색에 사용)
 */
@Repository
@RequiredArgsConstructor
public class ItemSearchRepositoryImpl implements ItemSearchRepository {

  private static final QItem ITEM = QItem.item;
  private static final QMember MEMBER = QMember.member;

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<Item> searchVisibleItems(ItemSearchCondition searchCondition, Pageable pageable) {
    BooleanExpression where = QueryDslUtil.allOf(
        ItemFeedQueryExpressions.buildFeedVisibilityCondition(searchCondition.viewerMemberId()),
        buildKeywordCondition(searchCondition.keyword()),
        buildCategoryCondition(searchCondition.itemCategories()),
        buildExcludedItemCondition(searchCondition.excludedItemIds())
    );

    JPAQuery<Item> contentQuery = queryFactory
        .selectFrom(ITEM)
        .join(ITEM.member, MEMBER).fetchJoin()
        .where(where)
        .orderBy(buildOrderSpecifiers(searchCondition));

    JPAQuery<Long> countQuery = queryFactory
        .select(ITEM.count())
        .from(ITEM)
        .where(where);

    return QueryDslUtil.fetchPage(contentQuery, countQuery, pageable);
  }

  /**
   * 검색어: 물품명 또는 설명 포함 (대소문자 무시)
   */
  private BooleanExpression buildKeywordCondition(String keyword) {
    return ITEM.itemName.containsIgnoreCase(keyword)
        .or(ITEM.itemDescription.containsIgnoreCase(keyword));
  }

  /**
   * 카테고리 필터 (비어 있으면 조건 없음)
   */
  private BooleanExpression buildCategoryCondition(List<ItemCategory> itemCategories) {
    if (itemCategories == null || itemCategories.isEmpty()) {
      return null;
    }
    return ITEM.itemCategory.in(itemCategories);
  }

  /**
   * 제외 물품 (내가 신고한 물품 등, 비어 있으면 조건 없음)
   */
  private BooleanExpression buildExcludedItemCondition(Collection<UUID> excludedItemIds) {
    if (excludedItemIds == null || excludedItemIds.isEmpty()) {
      return null;
    }
    return ITEM.itemId.notIn(excludedItemIds);
  }

  /**
   * 정렬: 최신순(기본), 거리순(좌표 없으면 최신순), 가격 오름·내림 > 동률은 최신순
   */
  private OrderSpecifier<?>[] buildOrderSpecifiers(ItemSearchCondition searchCondition) {
    OrderSpecifier<?> latestFirst = new OrderSpecifier<>(Order.DESC, ITEM.createdDate);
    return switch (searchCondition.sortField()) {
      case DISTANCE -> searchCondition.hasLocation()
          ? new OrderSpecifier<?>[]{
              new OrderSpecifier<>(Order.ASC, ItemFeedQueryExpressions.buildDistanceExpression(
                  searchCondition.longitude(), searchCondition.latitude())),
              latestFirst}
          : new OrderSpecifier<?>[]{latestFirst};
      case PRICE_ASC -> new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.ASC, ITEM.price), latestFirst};
      case PRICE_DESC -> new OrderSpecifier<?>[]{new OrderSpecifier<>(Order.DESC, ITEM.price), latestFirst};
      case LATEST -> new OrderSpecifier<?>[]{latestFirst};
    };
  }
}
