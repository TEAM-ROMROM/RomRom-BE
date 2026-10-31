package com.romrom.application.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.romrom.common.constant.ActivityAggregation;
import com.romrom.common.constant.ActivityBucketUnit;
import com.romrom.common.constant.ActivityEventType;
import com.romrom.common.entity.mongo.UserActivityEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 활동 이벤트 집계 실제 Mongo 검증: 버킷 라벨(KST), 고유 회원 수, 최대값, 기간 경계
 * 환경변수 ROMROM_TEST_MONGO_URI(예: mongodb://127.0.0.1:27944) 있을 때만 실행
 */
@Tag("integration")
class AdminTrafficAnalyticsQueryMongoTest {

  private static final String TEST_MONGO_URI_ENV = "ROMROM_TEST_MONGO_URI";
  private static final ZoneId KST_ZONE_ID = ZoneId.of("Asia/Seoul");

  private MongoClient mongoClient;
  private MongoTemplate mongoTemplate;
  private AdminTrafficAnalyticsQuery adminTrafficAnalyticsQuery;

  @BeforeEach
  void connectMongo() {
    String testMongoUri = System.getenv(TEST_MONGO_URI_ENV);
    Assumptions.assumeTrue(testMongoUri != null && !testMongoUri.isBlank(), "테스트 Mongo 미지정");
    // 운영 설정과 같은 UUID 표현 (spring.data.mongodb.uuid-representation: STANDARD)
    mongoClient = MongoClients.create(MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(testMongoUri))
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .build());
    mongoTemplate = new MongoTemplate(mongoClient, "romrom_traffic_test_" + UUID.randomUUID().toString().substring(0, 8));
    adminTrafficAnalyticsQuery = new AdminTrafficAnalyticsQuery(mongoTemplate);
  }

  @AfterEach
  void dropTestDatabase() {
    if (mongoTemplate != null) {
      mongoTemplate.getDb().drop();
      mongoClient.close();
    }
  }

  // KST 벽시계 시각 > 시스템 시간대 (서비스와 같은 변환)
  private static LocalDateTime kst(int day, int hour, int minute) {
    return LocalDateTime.of(2026, 11, day, hour, minute).atZone(KST_ZONE_ID)
        .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
  }

  private void insertApiCall(UUID memberId, LocalDateTime occurredAt) {
    mongoTemplate.save(UserActivityEvent.of(ActivityEventType.API_CALL, memberId, null, null, occurredAt));
  }

  private void insertConcurrentSnapshot(long onlineMemberCount, LocalDateTime occurredAt) {
    mongoTemplate.save(UserActivityEvent.of(ActivityEventType.CONCURRENT_USERS, null, onlineMemberCount, null, occurredAt));
  }

  @Test
  @DisplayName("일 버킷: KST 날짜 경계로 나뉘고 고유 회원은 중복·비로그인 제외")
  void 일별_고유_회원() {
    UUID firstMemberId = UUID.randomUUID();
    UUID secondMemberId = UUID.randomUUID();
    insertApiCall(firstMemberId, kst(1, 23, 50));
    insertApiCall(firstMemberId, kst(1, 23, 55));
    insertApiCall(null, kst(1, 12, 0));
    insertApiCall(firstMemberId, kst(2, 0, 10));
    insertApiCall(secondMemberId, kst(2, 9, 0));

    Map<String, Long> distinctMembersByDay = adminTrafficAnalyticsQuery.aggregateByBucket(ActivityEventType.API_CALL,
        ActivityAggregation.DISTINCT_MEMBERS, ActivityBucketUnit.DAY, kst(1, 0, 0), kst(3, 0, 0));
    Map<String, Long> apiCallsByDay = adminTrafficAnalyticsQuery.aggregateByBucket(ActivityEventType.API_CALL,
        ActivityAggregation.EVENT_COUNT, ActivityBucketUnit.DAY, kst(1, 0, 0), kst(3, 0, 0));

    Assertions.assertEquals(Map.of("2026-11-01", 1L, "2026-11-02", 2L), distinctMembersByDay);
    Assertions.assertEquals(Map.of("2026-11-01", 3L, "2026-11-02", 2L), apiCallsByDay);
  }

  @Test
  @DisplayName("시간 버킷: 동시접속자 스냅샷은 시간당 최대값")
  void 시간별_동시접속자_최대값() {
    insertConcurrentSnapshot(3, kst(1, 13, 0));
    insertConcurrentSnapshot(8, kst(1, 13, 25));
    insertConcurrentSnapshot(5, kst(1, 14, 5));

    Map<String, Long> maxOnlineByHour = adminTrafficAnalyticsQuery.aggregateByBucket(ActivityEventType.CONCURRENT_USERS,
        ActivityAggregation.MAX_METRIC_VALUE, ActivityBucketUnit.HOUR, kst(1, 0, 0), kst(2, 0, 0));

    Assertions.assertEquals(Map.of("2026-11-01 13:00", 8L, "2026-11-01 14:00", 5L), maxOnlineByHour);
  }

  @Test
  @DisplayName("오늘 집계: 기간 밖 이벤트 제외, 이벤트 수·고유 회원 수")
  void 기간_경계와_실시간_집계() {
    UUID memberId = UUID.randomUUID();
    insertApiCall(memberId, kst(1, 23, 59));
    insertApiCall(memberId, kst(2, 0, 0));
    insertApiCall(memberId, kst(2, 10, 0));
    insertApiCall(null, kst(2, 11, 0));

    Assertions.assertEquals(3L, adminTrafficAnalyticsQuery.countEvents(ActivityEventType.API_CALL, kst(2, 0, 0), kst(3, 0, 0)));
    Assertions.assertEquals(1L, adminTrafficAnalyticsQuery.countDistinctMembers(ActivityEventType.API_CALL, kst(2, 0, 0), kst(3, 0, 0)));
  }
}
