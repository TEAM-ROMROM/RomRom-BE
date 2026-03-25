package com.romrom.member.entity.mongo;

import com.romrom.common.entity.mongo.BaseMongoEntity;
import java.time.LocalDateTime;
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
public class SanctionHistory extends BaseMongoEntity {

  @Id
  private String sanctionHistoryId;

  @Indexed
  private UUID memberId;

  private String suspendReason;

  private LocalDateTime suspendedAt;

  private LocalDateTime suspendedUntil;

  private LocalDateTime liftedAt;

  private String liftedReason;

  private UUID reportId;

  private String reportType;
}
