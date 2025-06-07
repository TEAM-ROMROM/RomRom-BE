package com.romrom.romback.domain.object.postgres;

import com.romrom.romback.domain.object.constant.OriginalType;
import com.romrom.romback.global.converter.PgVectorType;
import com.romrom.romback.global.util.BasePostgresEntity;
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
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

@Entity
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ToString(callSuper = true)
public class Embedding extends BasePostgresEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID embeddingId;

  private UUID originalId;

  @Type(PgVectorType.class)
  @Column(columnDefinition = "vector(768)")
  private float[] embedding;

  private OriginalType originalType;
}
