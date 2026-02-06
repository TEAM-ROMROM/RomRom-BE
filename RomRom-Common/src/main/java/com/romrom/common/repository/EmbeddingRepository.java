package com.romrom.common.repository;

import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmbeddingRepository extends JpaRepository<Embedding, UUID> {

  Optional<Embedding> findByEmbeddingId(UUID embeddingId);

  int deleteByOriginalIdAndOriginalType(UUID originalId, OriginalType originalType);

  Optional<Embedding> findByOriginalIdAndOriginalType(UUID originalId, OriginalType originalType);

  @Query(
      value = """
      SELECT e.original_id
      FROM embedding e
      WHERE e.original_type = 0
        AND e.original_id IN :itemIds
      ORDER BY e.embedding <=> cast(:targetVector AS vector)
      """,
      countQuery = """
      SELECT COUNT(*)
      FROM embedding e
      WHERE e.original_type = 0
        AND e.original_id IN :itemIds
      """,
      nativeQuery = true
  )
  Page<UUID> findSimilarItemIds(
      @Param("itemIds") List<UUID> itemIds,
      @Param("targetVector") String targetVector,
      Pageable pageable
  );

  @Query(value = "SELECT e.original_id " +
                 "FROM embedding e " +
                 "WHERE e.original_id IN :myItemIds " +
                 "AND e.original_type = 0 " + // OriginalType.ITEM의 ORDINAL 값인 0 사용
                 "ORDER BY e.embedding <=> CAST(:targetVector AS vector) ASC",
      countQuery = "SELECT count(*) FROM embedding e " +
                   "WHERE e.original_id IN :myItemIds AND e.original_type = 0",
      nativeQuery = true)
  Page<UUID> findRecommendedItemIds(
      @Param("myItemIds") List<UUID> myItemIds,
      @Param("targetVector") String targetVector,
      Pageable pageable
  );

  @Modifying
  @Query("DELETE FROM Embedding e WHERE e.originalId IN :ids AND e.originalType = :type")
  void deleteAllByOriginalIdsAndType(@Param("ids") List<UUID> ids, @Param("type") OriginalType type);
} 