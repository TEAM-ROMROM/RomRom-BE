package com.romrom.common.repository;

import com.romrom.common.entity.postgres.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmbeddingRepository extends JpaRepository<Embedding, UUID> {
    Optional<Embedding> findByEmbeddingId(UUID embeddingId);
} 