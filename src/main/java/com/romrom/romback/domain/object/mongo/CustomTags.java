package com.romrom.romback.domain.object.mongo;

import com.romrom.romback.global.util.BaseMongoEntity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "custom_tags")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EnableMongoAuditing
public class CustomTags extends BaseMongoEntity {

  @Id
  private String id;

  @Indexed
  @NotNull
  private UUID itemId;

  @NotNull
  private List<String> customTags;

  // 서비스의 비즈니스로직을 도메인 메서드로 분리
  public void updateTags(List<String> tags) {
    this.customTags = tags;
  }
}
