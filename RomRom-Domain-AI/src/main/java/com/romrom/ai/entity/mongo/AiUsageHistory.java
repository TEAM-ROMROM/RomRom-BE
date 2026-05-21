package com.romrom.ai.entity.mongo;

import com.romrom.common.constant.AiUsageType;
import com.romrom.common.entity.mongo.BaseMongoEntity;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "member_requestedAt_idx", def = "{ 'memberId': 1, 'requestedAt': -1 }"),
    @CompoundIndex(name = "relatedEntity_requestedAt_idx", def = "{ 'relatedEntityId': 1, 'requestedAt': -1 }")
})
public class AiUsageHistory extends BaseMongoEntity {

  @Id
  private String aiUsageHistoryId;

  @Indexed
  private UUID memberId;

  private AiUsageType aiUsageType;

  @Indexed
  private LocalDateTime requestedAt;

  @Indexed
  private UUID relatedEntityId;

  private Map<String, Object> requestPayload;

  private Map<String, Object> responsePayload;

  private Boolean isSuccess;

  private String errorMessage;

  private Long durationMs;

  private String modelName;
}
