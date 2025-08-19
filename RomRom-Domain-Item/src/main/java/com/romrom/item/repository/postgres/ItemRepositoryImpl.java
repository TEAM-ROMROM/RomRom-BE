package com.romrom.item.repository.postgres;

import com.romrom.common.constant.SortType;
import com.romrom.common.constant.OriginalType; // ✅ 추가
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
      Pageable pageable) {

    Map<String, Object> dataParams = new HashMap<>();
    Map<String, Object> countParams = new HashMap<>();

    // 1) SELECT
    StringBuilder sql = new StringBuilder("SELECT i.* FROM item i ");
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM item i ");

    // 2) 선호 카테고리 JOIN
    if (sortType == SortType.PREFERRED_CATEGORY) {
      sql.append("JOIN embedding e ON e.original_id = i.item_id AND e.original_type = :originalType ");
      countSql.append("JOIN embedding e ON e.original_id = i.item_id AND e.original_type = :originalType ");

      // ✅ enum ordinal 값 세팅 (DB smallint 저장이므로)
      int ordinal = OriginalType.ITEM.ordinal();
      dataParams.put("originalType", ordinal);
      countParams.put("originalType", ordinal);
    }

    // 3) WHERE 공통
    sql.append("WHERE i.is_deleted = false AND i.member_member_id != :memberId ");
    countSql.append("WHERE i.is_deleted = false AND i.member_member_id != :memberId ");
    dataParams.put("memberId", memberId);
    countParams.put("memberId", memberId);

    // 4) 정렬 방향
    String direction = pageable.getSort().iterator().next().getDirection().isAscending() ? "ASC" : "DESC";

    switch (sortType) {
      case DISTANCE -> {
        sql.append("""
                AND ST_DWithin(
                  i.location::geography,
                  ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                  :radius
                )
                ORDER BY ST_Distance(
                  i.location::geography,
                  ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
                )
                """)
            .append(' ')
            .append(direction)
            .append(' ');

        countSql.append("""
            AND ST_DWithin(
              i.location::geography,
              ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
              :radius
            )
            """);

        dataParams.put("lon", longitude);
        dataParams.put("lat", latitude);
        dataParams.put("radius", radius);
        countParams.put("lon", longitude);
        countParams.put("lat", latitude);
        countParams.put("radius", radius);
      }

      case PREFERRED_CATEGORY -> {
        sql.append("ORDER BY (e.embedding <=> CAST(:embedding AS vector)) ")
            .append(direction)
            .append(' ');
        dataParams.put("embedding", memberEmbedding);
      }

      case CREATED_DATE -> {
        sql.append("ORDER BY i.created_date ")
            .append(direction)
            .append(' ');
      }
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
