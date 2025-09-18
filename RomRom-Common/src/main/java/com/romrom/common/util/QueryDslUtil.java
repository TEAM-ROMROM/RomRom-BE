package com.romrom.common.util;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQuery;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

@UtilityClass
public class QueryDslUtil {

  /**
   * 두 BooleanExpression을 AND 조건으로 결합
   * - baseClause가 null이면 additionalClause 그대로 반환
   * - additionalClause가 null이면 baseClause 그대로 반환
   * - 둘 다 null이 아니면 baseClause.and(additionalClause) 반환
   *
   * @param baseClause       기존 WHERE 절
   * @param additionalClause 추가할 WHERE 절
   * @return 조합된 최종 WHERE 절
   * @see BooleanExpression#and(Predicate)
   */
  public BooleanExpression combineWhereClause(BooleanExpression baseClause, BooleanExpression additionalClause) {
    if (additionalClause == null) {
      return baseClause;
    }
    return (baseClause == null) ? additionalClause : baseClause.and(additionalClause);
  }

  /**
   * 여러 BooleanExpression을 하나의 AND 절로 결합
   * - 전달된 expressions 순서대로 null 체크 후 AND 연산
   * - 모든 expression이 null이면 null반환
   *
   * @param expressions 결합할 BooleanExpression 배열
   * @return 결합된 BooleanExpression 또는 모두 null 일 경우 null
   */
  public BooleanExpression allOf(BooleanExpression... expressions) {
    BooleanExpression result = null;
    if (expressions == null || expressions.length == 0) {
      return null;
    }
    for (BooleanExpression expression : expressions) {
      result = combineWhereClause(result, expression);
    }
    return result;
  }

  /**
   * 여러 BooleanExpression을 하나의 OR 절로 결합
   * - 전달된 expressions 순서대로 null 체크 후 OR 연산
   * - 모든 expression이 null이면 null반환
   *
   * @param expressions 결합할 BooleanExpression 배열
   * @return 결합된 BooleanExpression 또는 모두 null 일 경우 null
   */
  public BooleanExpression anyOf(BooleanExpression... expressions) {
    BooleanExpression result = null;
    if (expressions == null || expressions.length == 0) {
      return null;
    }
    for (BooleanExpression expression : expressions) {
      if (expression != null) {
        result = (result == null) ? expression : result.or(expression);
      }
    }
    return result;
  }

  /**
   * 주어진 값(value)이 null이 아닐 때만 EQ(=) 조건 생성
   * - value가 null이면 null을 반환하여 WHERE 절에 해당 조건이 포함되지 않게 합니다
   * - value가 null이 아니면 path.eq(value) 조건을 반환합니다
   *
   * @param <T>   SimpleExpression이 처리하는 값의 타입
   * @param path  QueryDSL의 SimpleExpression 필드 경로 ex) QEntity.field
   * @param value 비교할 실제 값
   * @return value가 null이 아닐 경우 path.eq(value), null 일 경우 null
   * @see SimpleExpression#eq(Object)
   */
  public <T> BooleanExpression eqIfNotNull(SimpleExpression<T> path, T value) {
    if (value == null) {
      return null;
    }
    return path.eq(value);
  }

  /**
   * 주어진 값(value)이 null이 아닐 때만 NE(!=) 조건 생성
   * - value가 null이면 null을 반환하여 WHERE 절에 해당 조건이 포함되지 않게 합니다
   * - value가 null이 아니면 path.ne(value) 조건을 반환합니다
   *
   * @param <T>   SimpleExpression이 처리하는 값의 타입
   * @param path  QueryDSL의 SimpleExpression 필드 경로 ex) QEntity.field
   * @param value 비교할 실제 값
   * @return value가 null이 아닐 경우 path.ne(value), null 일 경우 null
   * @see SimpleExpression#ne(Object)
   */
  public <T> BooleanExpression neIfNotNull(SimpleExpression<T> path, T value) {
    if (value == null) {
      return null;
    }
    return path.ne(value);
  }

  /**
   * value가 null 또는 빈 문자열이 아닐 때만 대소문자 구문 없는 LIKE 조건 생성
   * - value가 null 이거나 공백만 있을 경우 null을 반환하여 WHERE 절에서 생략
   * - value가 유효하면 path.lower().like("%value%") 반환
   *
   * @param path  Q 클래스의 StringExpression 경로
   * @param value 검색할 키워드
   * @return 대소문자 구분 없는 LIKE 조건 혹은 null
   * @see StringExpression#lower()
   * @see StringExpression#like(String)
   */
  public BooleanExpression likeIgnoreCase(StringExpression path, String value) {
    if (CommonUtil.nvl(value, "").isEmpty()) {
      return null;
    }
    return path.lower().like("%" + value.trim().toLowerCase() + "%");
  }

  /**
   * ComparableExpression 경로와 정렬 방향으로 OrderSpecifier 생성
   *
   * @param <T>  ComparableExpression의 타입
   * @param path 정렬 대상 경로 (예: QEntity.createdDate)
   * @param asc  true이면 오름차순, false이면 내림차순
   * @return OrderSpecifier 객체
   */
  public <T extends Comparable<?>> OrderSpecifier<T> createOrderSpecifier(ComparableExpression<T> path, boolean asc) {
    return new OrderSpecifier<>(asc ? Order.ASC : Order.DESC, path);
  }

  /**
   * JPAQuery에 동적 정렬을 적용
   * PathBuilder를 통해 프로퍼티명을 직접 참조
   * 각 엔티티별 공통 활용
   *
   * @param <D>         query의 projection(DTO) 타입
   * @param <E>         정렬 대상이 되는 엔티티 타입
   * @param query       정렬할 JPAQuery
   * @param pageable    Spring Data Pageable
   * @param entityClass 정렬할 필드를 가진 엔티티 클래스
   * @param alias       QueryDSL 별명 (ex. "entity")
   */
  public <D, E> void applySorting(JPAQuery<D> query, Pageable pageable, Class<E> entityClass, String alias) {
    applySorting(query, pageable, entityClass, alias, Collections.emptyMap());
  }

  /**
   * JPAQuery에 동적 정렬을 적용
   * PathBuilder를 통해 프로퍼티명을 직접 참조
   * customSortMap에 프로퍼티명이 매핑된 ComparableExpression이 있으면 사용
   *
   * @param <D>           query의 projection(DTO) 타입
   * @param <E>           정렬 대상이 되는 엔티티 타입
   * @param query         정렬할 JPAQuery
   * @param pageable      Spring Data Pageable
   * @param entityClass   정렬할 필드를 가진 엔티티 클래스
   * @param alias         QueryDSL에서 사용되는 엔티티 별칭 (ex. "entity")
   * @param customSortMap 프로퍼티명과 {@link ComparableExpression}을 매핑한 사용자 정의 정렬 Map
   */
  public <D, E> void applySorting(
      JPAQuery<D> query,
      Pageable pageable,
      Class<E> entityClass, String alias,
      Map<String, ComparableExpression<?>> customSortMap
  ) {
    if (pageable.getSort().isEmpty()) {
      return;
    }
    PathBuilder<E> builder = new PathBuilder<>(entityClass, alias);
    for (Sort.Order order : pageable.getSort()) {
      String property = order.getProperty();
      boolean asc = order.isAscending();

      // enum.getProperty() 로 넘어온 커스텀 표현식이 있으면 우선 사용
      ComparableExpression<?> expression = customSortMap.get(property);
      if (expression == null) {
        // 없으면 엔티티 필드로 처리
        expression = builder.getComparable(property, Comparable.class);
      }
      query.orderBy(createOrderSpecifier(expression, asc));
    }
  }

  /**
   * QueryDSL JPAQuery를 이용한 페이징 처리
   * - contentQuery: 페이징 설정(offset, limit) 및 정렬이 적용된 JPAQuery
   * - countQuery: 전체 건수를 조회하기 위한 JPAQuery (select count)
   * - 두 개의 쿼리를 실행하여 Page 객체로 반환
   *
   * @param <T>          조회 엔티티 또는 DTO 타입
   * @param contentQuery offset, limit, orderBy가 설정된 JPAQuery
   * @param countQuery   전체 레코드 수를 조회하는 JPAQuery<Long>
   * @param pageable     Spring Data Pageable
   * @return 페이징된 결과
   */
  public <T> Page<T> fetchPage(JPAQuery<T> contentQuery, JPAQuery<Long> countQuery, Pageable pageable) {
    List<T> content = contentQuery
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize())
        .fetch();

    Long total = countQuery.fetchOne();
    total = (total == null) ? 0L : total;

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * QueryDSL JPAQuery를 이용한 Slice 페이징 처리
   * - count 쿼리 없이, '다음 페이지 존재 여부'만 확인
   * - 무한 스크롤 방식에 최적화
   *
   * @param <T>          조회 엔티티 또는 DTO 타입
   * @param contentQuery offset, limit, orderBy가 설정된 JPAQuery
   * @param pageable     Spring Data Pageable
   * @return Slice 페이징 결과
   */
  public <T> Slice<T> fetchSlice(JPAQuery<T> contentQuery, Pageable pageable) {
    List<T> content = contentQuery
        .offset(pageable.getOffset())
        .limit(pageable.getPageSize() + 1)
        .fetch();

    boolean hasNext = false;
    if (content.size() > pageable.getPageSize()) {
      content.remove(pageable.getPageSize());
      hasNext = true;
    }

    return new SliceImpl<>(content, pageable, hasNext);
  }
}
