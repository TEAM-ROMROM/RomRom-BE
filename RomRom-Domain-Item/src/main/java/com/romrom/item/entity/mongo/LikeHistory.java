package com.romrom.item.entity.mongo;

import com.romrom.common.constant.LikeContentType;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LikeHistory extends BaseMongoEntity {

  @Id
  private String likeHistoryId;

  @Indexed
  @NotNull
  private UUID memberId;

  @Indexed
  @NotNull
  private UUID itemId;

  private LikeContentType likeContentType;
}
