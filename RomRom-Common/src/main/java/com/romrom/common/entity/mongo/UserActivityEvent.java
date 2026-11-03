package com.romrom.common.entity.mongo;

import com.romrom.common.constant.ActivityEventCategory;
import com.romrom.common.constant.ActivityEventType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 공통 활동 이벤트 스트림: 모든 트래픽 지표를 이 컬렉션 하나에서 집계로 파생
 * 고정 필드 + 확장 properties, IP·UserAgent는 저장하지 않음, occurredAt 기준 90일 TTL
 */
@Document
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "eventType_occurredAt_idx", def = "{'eventType': 1, 'occurredAt': -1}"),
    @CompoundIndex(name = "eventCategory_occurredAt_idx", def = "{'eventCategory': 1, 'occurredAt': -1}")
})
public class UserActivityEvent {

  // 원본 이벤트 보관 기간 (90일, 초)
  public static final int RETENTION_SECONDS = 90 * 24 * 60 * 60;

  @Id
  private String userActivityEventId;

  private ActivityEventType eventType;

  private ActivityEventCategory eventCategory;

  // 비로그인 요청은 null
  private UUID memberId;

  // 스냅샷형 이벤트 값 (CONCURRENT_USERS: 온라인 회원 수), 그 외 null
  private Long metricValue;

  // 이벤트별 자유 확장 필드 (예: pathPattern, statusCode)
  @Builder.Default
  private Map<String, Object> properties = new HashMap<>();

  @Indexed(name = "occurredAt_ttl_idx", expireAfterSeconds = RETENTION_SECONDS)
  private LocalDateTime occurredAt;

  /**
   * 이벤트 종류 > 대분류 자동 지정 후 생성
   */
  public static UserActivityEvent of(ActivityEventType eventType, UUID memberId, Long metricValue,
      Map<String, Object> properties, LocalDateTime occurredAt) {
    return UserActivityEvent.builder()
        .eventType(eventType)
        .eventCategory(eventType.getEventCategory())
        .memberId(memberId)
        .metricValue(metricValue)
        .properties(properties != null ? properties : new HashMap<>())
        .occurredAt(occurredAt)
        .build();
  }
}
