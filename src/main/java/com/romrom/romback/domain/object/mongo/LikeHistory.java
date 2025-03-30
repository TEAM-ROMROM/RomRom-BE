package com.romrom.romback.domain.object.mongo;

import com.romrom.romback.domain.object.constant.ItemCategory;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LikeHistory {

  @Id
  private String likeHistoryId;

  @Indexed
  @NotNull
  private UUID memberId;

  @Indexed
  @NotNull
  private UUID itemId;

  private ItemCategory itemCategory;  // ENUM 으로 무슨 항목을 구분...?
}
