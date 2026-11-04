package com.romrom.item.repository.postgres;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.romrom.common.constant.AccountStatus;
import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.util.QueryDslUtil;
import com.romrom.item.entity.postgres.QHiddenItem;
import com.romrom.item.entity.postgres.QItem;
import com.romrom.member.entity.QMemberBlock;
import java.util.List;
import java.util.UUID;

/**
 * 물품 목록 공통 조건식: 노출 조건(홈 피드·롬업 후보·검색 공통), 반경 안 여부, 거리, 최근 본 물품 뒤로, 선호 카테고리 (#862)
 */
final class ItemFeedQueryExpressions {

  private static final QItem ITEM = QItem.item;

  private ItemFeedQueryExpressions() {
  }

  /**
   * 노출 조건 7개: 본인 물품 제외, 미삭제, 관리자 차단 제외, 교환 가능, 정지 회원 제외, 상호 차단 제외, 다시 보지 않기 제외
   */
  static BooleanExpression buildFeedVisibilityCondition(UUID memberId) {
    QMemberBlock qBlock = QMemberBlock.memberBlock;
    QHiddenItem qHiddenItem = QHiddenItem.hiddenItem;

    // 차단 관계가 아닌 것만 조회
    BooleanExpression notBlocked = JPAExpressions
        .selectOne()
        .from(qBlock)
        .where(
            (qBlock.blockerMember.memberId.eq(memberId).and(qBlock.blockedMember.memberId.eq(ITEM.member.memberId)))
                .or(qBlock.blockerMember.memberId.eq(ITEM.member.memberId).and(qBlock.blockedMember.memberId.eq(memberId)))
        )
        .notExists();

    // 다시 보지 않기 설정한 물품 제외
    BooleanExpression notHidden = JPAExpressions
        .selectOne()
        .from(qHiddenItem)
        .where(
            qHiddenItem.member.memberId.eq(memberId)
                .and(qHiddenItem.item.itemId.eq(ITEM.itemId))
        )
        .notExists();

    return QueryDslUtil.allOf(
        QueryDslUtil.neIfNotNull(ITEM.member.memberId, memberId),
        ITEM.isDeleted.isFalse(),
        ITEM.isAdminHidden.isFalse(),  // 관리자 노출 차단 물품 제외
        ITEM.itemStatus.eq(ItemStatus.AVAILABLE),
        ITEM.member.accountStatus.ne(AccountStatus.SUSPENDED_ACCOUNT),  // 정지 회원 물품 비노출
        notBlocked,     // 차단 필터 적용
        notHidden       // 숨긴 물품 제외
    );
  }

  /**
   * 기준 좌표에서 반경(미터) 안 물품
   */
  static BooleanExpression buildWithinRadiusCondition(Double longitude, Double latitude, Double radiusInMeters) {
    return Expressions.booleanTemplate(
        "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326)) <= {3}",
        ITEM.location, longitude, latitude, radiusInMeters
    );
  }

  /**
   * 기준 좌표 > 물품 위치 거리 (미터, 정렬용)
   */
  static NumberExpression<Double> buildDistanceExpression(Double longitude, Double latitude) {
    return Expressions.numberTemplate(
        Double.class,
        "function('ST_DistanceSphere', {0}, function('ST_SetSRID', function('ST_MakePoint', {1}, {2}), 4326))",
        ITEM.location, longitude, latitude
    );
  }

  /**
   * 최근 본 물품 정렬: 본 물품 1, 나머지 0 > 오름차순 (안 본 물품 먼저)
   */
  static OrderSpecifier<Integer> buildRecentlySeenLastOrder(List<UUID> recentlySeenItemIds) {
    NumberExpression<Integer> recentlySeenRankExpr = new CaseBuilder()
        .when(ITEM.itemId.in(recentlySeenItemIds)).then(1)
        .otherwise(0);
    return new OrderSpecifier<>(Order.ASC, recentlySeenRankExpr);
  }

  /**
   * 명시적 선호 필터
   * 사용자가 설정한 선호 카테고리에 아이템이 속하는지 체크 (True/False)
   */
  static BooleanExpression buildPreferredCategoryPredicate(List<ItemCategory> preferredCategories) {
    if (preferredCategories == null || preferredCategories.isEmpty())
      return Expressions.FALSE.isTrue();
    BooleanExpression predicate = null;
    for (ItemCategory cat : preferredCategories) {
      predicate = (predicate == null) ? ITEM.itemCategory.eq(cat) : predicate.or(ITEM.itemCategory.eq(cat));
    }
    return predicate;
  }
}
