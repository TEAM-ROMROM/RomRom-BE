package com.romrom.romback.domain.repository.postgres;

import com.romrom.romback.domain.object.dto.HashRegistry;
import com.romrom.romback.domain.object.constant.HashType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashRegistryRepository extends JpaRepository<HashRegistry, UUID> {
  Optional<HashRegistry> findByHashType(HashType hashType);
}
