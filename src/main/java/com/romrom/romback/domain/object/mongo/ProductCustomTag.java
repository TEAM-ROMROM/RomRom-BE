package com.romrom.romback.domain.object.mongo;

import com.romrom.romback.global.util.BaseMongoEntity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCustomTag extends BaseMongoEntity {

  @Id
  private String productCustomTagId;

  @Indexed
  @NotNull
  private UUID productId;

  @NotNull
  private String customTag;
}
