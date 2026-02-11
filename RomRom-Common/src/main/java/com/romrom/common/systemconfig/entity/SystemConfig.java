package com.romrom.common.systemconfig.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SystemConfig extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID systemConfigId;

  @Column(nullable = false, unique = true, length = 100)
  private String configKey;

  @Column(columnDefinition = "TEXT")
  private String configValue;

  @Column(length = 500)
  private String description;
}
