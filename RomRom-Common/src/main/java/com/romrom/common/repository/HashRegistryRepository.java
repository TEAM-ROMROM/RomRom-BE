package com.romrom.common.repository;

import com.romrom.common.constant.HashType;
import com.romrom.common.entity.postgres.HashRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HashRegistryRepository extends JpaRepository<HashRegistry, UUID> {
  Optional<HashRegistry> findByHashType(HashType hashType);
}
