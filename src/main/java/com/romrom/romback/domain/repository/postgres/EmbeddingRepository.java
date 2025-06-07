package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.postgres.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmbeddingRepository extends JpaRepository<Embedding, UUID> {
    Optional<Embedding> findByEmbeddingId(UUID embeddingId);
}
