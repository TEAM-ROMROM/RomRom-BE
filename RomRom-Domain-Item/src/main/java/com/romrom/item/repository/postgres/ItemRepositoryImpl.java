package com.romrom.item.repository.postgres;

import com.romrom.common.constant.ItemCategory;
import com.romrom.common.constant.ItemCondition;
import com.romrom.common.constant.ItemStatus;
import com.romrom.common.constant.SortType;
import com.romrom.common.constant.OriginalType; // ✅ 추가
import com.romrom.item.entity.postgres.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
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
