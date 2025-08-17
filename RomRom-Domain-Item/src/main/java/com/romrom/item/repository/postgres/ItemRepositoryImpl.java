package com.romrom.item.repository.postgres;

import com.romrom.common.constant.SortDirection;
import com.romrom.common.constant.SortType;
import com.romrom.item.entity.postgres.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ItemRepositoryImpl implements ItemRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  public Page<Item> filterItems(UUID memberId,
      Double longitude,
      Double latitude,
      Double radius,
      float[] memberEmbedding,
      SortType sortType,
      SortDirection sortDirection,
      Pageable pageable) {

    StringBuilder sql = new StringBuilder();
    StringBuilder countSql = new StringBuilder();
    Map<String, Object> params = new HashMap<>();

    // SELECT
    sql.append("SELECT i.* FROM item i ");
    countSql.append("SELECT COUNT(*) FROM item i ");

    // 조건: 선호 카테고리 정렬이면 join embedding
    if (sortType == SortType.PREFERRED_CATEGORY) {
      sql.append("JOIN embedding e ON e.original_id = i.item_id AND e.original_type = 'ITEM' ");
      countSql.append("JOIN embedding e ON e.original_id = i.item_id AND e.original_type = 'ITEM' ");
    }

    // 공통 WHERE
    sql.append("WHERE i.is_deleted = false AND i.member_member_id != :memberId ");
    countSql.append("WHERE i.is_deleted = false AND i.member_member_id != :memberId ");
    params.put("memberId", memberId);

    // ORDER BY
    String direction = sortDirection.name();
    switch (sortType) {
      case DISTANCE -> {
        sql.append("""
            AND ST_DWithin(
              i.location::geography,
              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
              :radius
            )
            """);
        countSql.append("""
            AND ST_DWithin(
              i.location::geography,
              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
              :radius
            )
            """);

        sql.append("""
            ORDER BY ST_Distance(
              i.location::geography,
              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            ) """).append(direction).append(" ");

        params.put("lon", longitude);
        params.put("lat", latitude);
        params.put("radius", radius);
      }

      case PREFERRED_CATEGORY -> {
        sql.append("ORDER BY (e.embedding <=> :embedding) ").append(direction).append(" ");
        params.put("embedding", memberEmbedding);
      }

      case CREATED_DATE -> {
        sql.append("ORDER BY i.created_date ").append(direction).append(" ");
      }
    }

    // 실행
    Query dataQuery = entityManager.createNativeQuery(sql.toString(), Item.class);
    Query countQuery = entityManager.createNativeQuery(countSql.toString());

    params.forEach((k, v) -> {
      dataQuery.setParameter(k, v);
      countQuery.setParameter(k, v);
    });

    dataQuery.setFirstResult((int) pageable.getOffset());
    dataQuery.setMaxResults(pageable.getPageSize());

    List<Item> items = dataQuery.getResultList();
    Long total = ((Number) countQuery.getSingleResult()).longValue();

    return new PageImpl<>(items, pageable, total);
  }
}
