package com.romrom.common.systemconfig.repository;

import com.romrom.common.systemconfig.entity.SystemConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

  Optional<SystemConfig> findByConfigKey(String configKey);
}
