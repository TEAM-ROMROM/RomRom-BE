package com.romrom.common.repository;

import com.romrom.common.constant.OriginalType;
import com.romrom.common.entity.postgres.Embedding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
} 