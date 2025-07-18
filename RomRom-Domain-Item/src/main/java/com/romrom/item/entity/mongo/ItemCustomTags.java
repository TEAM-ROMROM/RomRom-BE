package com.romrom.item.entity.mongo;

import com.romrom.common.entity.mongo.BaseMongoEntity;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemCustomTags extends BaseMongoEntity {

  // import: jakarta persistence Id가 아니고 spring꺼로 해야함 !!!
  @Id
  private String itemCustomTagsId;

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
