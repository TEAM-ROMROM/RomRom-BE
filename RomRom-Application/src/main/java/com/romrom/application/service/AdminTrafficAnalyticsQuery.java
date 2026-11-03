package com.romrom.application.service;

import com.romrom.common.constant.ActivityAggregation;
import com.romrom.common.constant.ActivityBucketUnit;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.entity.mongo.UserActivityEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * 활동 이벤트 집계(Mongo, #772): 이벤트 종류·기간 > 버킷 라벨별 값 (이벤트 수, 고유 회원 수, 최대값)
 * 버킷 라벨은 KST 기준 $dateToString, LocalDateTime은 시스템 시간대로 Date 변환 (AdminActivityAnalyticsQuery와 동일)
 */
@Component
@RequiredArgsConstructor
public class AdminTrafficAnalyticsQuery {

  static final String KST_TIMEZONE = "Asia/Seoul";

  private final MongoTemplate mongoTemplate;

  /**
   * 이벤트 종류 + 기간 + 버킷 단위 + 계산 방식 > {버킷 라벨: 값} (이벤트 없는 버킷은 빠짐)
   */
  public Map<String, Long> aggregateByBucket(ActivityEventType eventType, ActivityAggregation aggregation,
      ActivityBucketUnit bucketUnit, LocalDateTime startDateTime, LocalDateTime endDateTime) {
    return runBucketAggregation(buildBucketPipeline(eventType, aggregation, bucketUnit, startDateTime, endDateTime));
  }

  /**
   * 기간 내 이벤트 수
   */
  public long countEvents(ActivityEventType eventType, LocalDateTime startDateTime, LocalDateTime endDateTime) {
    return mongoTemplate.getCollection(collectionName())
        .countDocuments(matchEventTypeBetween(eventType, startDateTime, endDateTime));
  }

  /**
   * 기간 내 이벤트를 남긴 고유 회원 수 (비로그인 제외)
   */
  public long countDistinctMembers(ActivityEventType eventType, LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        new Document("$match", matchEventTypeBetween(eventType, startDateTime, endDateTime)
            .append("memberId", new Document("$ne", null))),
        new Document("$group", new Document("_id", "$memberId")),
        new Document("$count", "count"));
    Document countResult = mongoTemplate.getCollection(collectionName()).aggregate(pipeline).first();
    Number distinctMemberCount = countResult != null ? countResult.get("count", Number.class) : null;
    return distinctMemberCount != null ? distinctMemberCount.longValue() : 0L;
  }

  /**
   * 버킷 파이프라인: 기간·종류 필터 > (고유 회원이면 회원별 1건으로 접기) > 버킷별 값
   */
  List<Document> buildBucketPipeline(ActivityEventType eventType, ActivityAggregation aggregation,
      ActivityBucketUnit bucketUnit, LocalDateTime startDateTime, LocalDateTime endDateTime) {
    Document eventMatch = matchEventTypeBetween(eventType, startDateTime, endDateTime);
    Document bucketLabel = new Document("$dateToString", new Document("format", bucketUnit.getMongoDateFormat())
        .append("date", "$occurredAt").append("timezone", KST_TIMEZONE));

    List<Document> pipeline = new ArrayList<>();
    switch (aggregation) {
      case DISTINCT_MEMBERS -> {
        pipeline.add(new Document("$match", eventMatch.append("memberId", new Document("$ne", null))));
        pipeline.add(new Document("$group", new Document("_id",
            new Document("bucket", bucketLabel).append("memberId", "$memberId"))));
        pipeline.add(new Document("$group", new Document("_id", "$_id.bucket")
            .append("value", new Document("$sum", 1))));
      }
      case MAX_METRIC_VALUE -> {
        pipeline.add(new Document("$match", eventMatch));
        pipeline.add(new Document("$group", new Document("_id", bucketLabel)
            .append("value", new Document("$max", "$metricValue"))));
      }
      default -> {
        pipeline.add(new Document("$match", eventMatch));
        pipeline.add(new Document("$group", new Document("_id", bucketLabel)
            .append("value", new Document("$sum", 1))));
      }
    }
    return pipeline;
  }

  private Map<String, Long> runBucketAggregation(List<Document> pipeline) {
    Map<String, Long> valueByBucketLabel = new HashMap<>();
    for (Document bucketDocument : mongoTemplate.getCollection(collectionName()).aggregate(pipeline)) {
      Object bucketKey = bucketDocument.get("_id");
      Number bucketValue = bucketDocument.get("value", Number.class);
      if (bucketKey != null) {
        valueByBucketLabel.put(bucketKey.toString(), bucketValue != null ? bucketValue.longValue() : 0L);
      }
    }
    return valueByBucketLabel;
  }

  private static Document matchEventTypeBetween(ActivityEventType eventType, LocalDateTime startDateTime,
      LocalDateTime endDateTime) {
    return new Document("eventType", eventType.name())
        .append("occurredAt", new Document("$gte", toDate(startDateTime)).append("$lt", toDate(endDateTime)));
  }

  private static Date toDate(LocalDateTime localDateTime) {
    return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  private String collectionName() {
    return mongoTemplate.getCollectionName(UserActivityEvent.class);
  }
}
