package com.romrom.common.repository;

import com.romrom.common.entity.postgres.SystemConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

  Optional<SystemConfig> findByConfigKey(String configKey);
}
