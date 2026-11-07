package com.romrom.application.service;

import com.romrom.common.constant.ActivityAggregation;
import com.romrom.common.constant.ServiceTimeZone;
import com.romrom.common.constant.ActivityBucketUnit;
import com.romrom.common.constant.ActivityEventType;
import java.time.LocalDateTime;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 활동 이벤트 버킷 파이프라인 단위 테스트: 계산 방식별 단계 구성, KST 버킷 형식
 */
class AdminTrafficAnalyticsQueryTest {

  private final AdminTrafficAnalyticsQuery adminTrafficAnalyticsQuery = new AdminTrafficAnalyticsQuery(null);

  private List<Document> buildPipeline(ActivityAggregation aggregation, ActivityBucketUnit bucketUnit) {
    return adminTrafficAnalyticsQuery.buildBucketPipeline(ActivityEventType.API_CALL, aggregation, bucketUnit,
        LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 2, 0, 0));
  }

  @Test
  @DisplayName("고유 회원 수: 비로그인 제외 > (버킷, 회원) 그룹 > 버킷별 회원 수")
  void 고유_회원_파이프라인() {
    List<Document> pipeline = buildPipeline(ActivityAggregation.DISTINCT_MEMBERS, ActivityBucketUnit.DAY);

    Assertions.assertEquals(3, pipeline.size());
    Document eventMatch = pipeline.get(0).get("$match", Document.class);
    Assertions.assertEquals("API_CALL", eventMatch.get("eventType"));
    Assertions.assertEquals(new Document("$ne", null), eventMatch.get("memberId"));
    Assertions.assertTrue(pipeline.get(1).get("$group", Document.class).get("_id", Document.class).containsKey("memberId"));
  }

  @Test
  @DisplayName("최대값: 버킷별 metricValue $max")
  void 최대값_파이프라인() {
    List<Document> pipeline = buildPipeline(ActivityAggregation.MAX_METRIC_VALUE, ActivityBucketUnit.HOUR);

    Document bucketGroup = pipeline.get(1).get("$group", Document.class);
    Assertions.assertEquals(new Document("$max", "$metricValue"), bucketGroup.get("value"));
    Document bucketLabel = bucketGroup.get("_id", Document.class).get("$dateToString", Document.class);
    Assertions.assertEquals(ActivityBucketUnit.HOUR.getMongoDateFormat(), bucketLabel.get("format"));
    Assertions.assertEquals(ServiceTimeZone.KST_ZONE_ID_NAME, bucketLabel.get("timezone"));
  }

  @Test
  @DisplayName("이벤트 수: 버킷별 $sum 1")
  void 이벤트_수_파이프라인() {
    List<Document> pipeline = buildPipeline(ActivityAggregation.EVENT_COUNT, ActivityBucketUnit.DAY);

    Assertions.assertEquals(2, pipeline.size());
    Assertions.assertEquals(new Document("$sum", 1), pipeline.get(1).get("$group", Document.class).get("value"));
  }
}
