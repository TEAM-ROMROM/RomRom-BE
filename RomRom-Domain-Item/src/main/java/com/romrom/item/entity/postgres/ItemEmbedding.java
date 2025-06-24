package com.romrom.item.entity.postgres;

import com.romrom.common.converter.PgVectorType;
import com.romrom.common.entity.postgres.BasePostgresEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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
public class ItemEmbedding extends BasePostgresEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(updatable = false, nullable = false)
  private UUID itemEmbeddingId;

  @ManyToOne(fetch = FetchType.LAZY)
  private Item item;

  @Type(PgVectorType.class)
  @Column(columnDefinition = "vector(384)")
  private float[] embedding;
}
