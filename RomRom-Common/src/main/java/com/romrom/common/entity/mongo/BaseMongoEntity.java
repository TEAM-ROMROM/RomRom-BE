package com.romrom.common.entity.mongo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class BaseMongoEntity {
  // 생성일
  @CreatedDate
  private LocalDateTime createdDate;

  // 수정일
  @LastModifiedDate
  private LocalDateTime updatedDate;
}
