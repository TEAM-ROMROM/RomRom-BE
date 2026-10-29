package com.romrom.application.service;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.chat.entity.mongo.ChatMessage;
import com.romrom.member.entity.mongo.LoginHistory;
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
 * 대시보드 활동 집계(Mongo, #827): 로그인 회원·시간대·기기, 채팅 메시지, AI 호출·실패·유형
 * 날짜·시는 KST 기준 ($dateToString·$hour timezone). LocalDateTime은 Spring Data와 같은 시스템 시간대로 Date 변환
 */
@Component
@RequiredArgsConstructor
public class AdminActivityAnalyticsQuery {

  private static final String KST_TIMEZONE = "Asia/Seoul";
  private static final String DAY_FORMAT = "%Y-%m-%d";
  // 로그인 이력 성공 값 (LoginResult.SUCCESS)
  private static final String LOGIN_SUCCESS = "SUCCESS";

  private final MongoTemplate mongoTemplate;

  /**
   * 날짜별 로그인 성공 고유 회원 수
   */
  public Map<String, Long> countDailyLoginMembers(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        matchLoginSuccess(startDateTime, endDateTime),
        new Document("$group", new Document("_id", new Document("day", toKstDay("$loginAt")).append("memberId", "$memberId"))),
        new Document("$group", new Document("_id", "$_id.day").append("count", new Document("$sum", 1))));
    return aggregateCountByKey(LoginHistory.class, pipeline);
  }

  /**
   * 시간대(0~23시)별 로그인 성공 건수, 24칸
   */
  public List<Long> countHourlyLogins(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        matchLoginSuccess(startDateTime, endDateTime),
        new Document("$group", new Document("_id",
            new Document("$hour", new Document("date", "$loginAt").append("timezone", KST_TIMEZONE)))
            .append("count", new Document("$sum", 1))));
    Map<String, Long> countByHour = aggregateCountByKey(LoginHistory.class, pipeline);
    List<Long> hourlyCounts = new ArrayList<>(24);
    for (int hour = 0; hour < 24; hour++) {
      hourlyCounts.add(countByHour.getOrDefault(String.valueOf(hour), 0L));
    }
    return hourlyCounts;
  }

  /**
   * 기기 종류별 로그인 성공 건수 (미기록은 OTHER)
   */
  public Map<String, Long> countLoginsByDeviceType(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        matchLoginSuccess(startDateTime, endDateTime),
        new Document("$group", new Document("_id", new Document("$ifNull", List.of("$deviceType", "OTHER")))
            .append("count", new Document("$sum", 1))));
    return aggregateCountByKey(LoginHistory.class, pipeline);
  }

  /**
   * 날짜별 채팅 메시지 수
   */
  public Map<String, Long> countDailyChatMessages(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        new Document("$match", new Document("createdDate", betweenDates(startDateTime, endDateTime))),
        new Document("$group", new Document("_id", toKstDay("$createdDate")).append("count", new Document("$sum", 1))));
    return aggregateCountByKey(ChatMessage.class, pipeline);
  }

  /**
   * 날짜별 AI 호출 수 (isOnlyFailures: 실패만)
   */
  public Map<String, Long> countDailyAiCalls(LocalDateTime startDateTime, LocalDateTime endDateTime, boolean isOnlyFailures) {
    Document matchCondition = new Document("requestedAt", betweenDates(startDateTime, endDateTime));
    if (isOnlyFailures) {
      matchCondition.append("isSuccess", false);
    }
    List<Document> pipeline = List.of(
        new Document("$match", matchCondition),
        new Document("$group", new Document("_id", toKstDay("$requestedAt")).append("count", new Document("$sum", 1))));
    return aggregateCountByKey(AiUsageHistory.class, pipeline);
  }

  /**
   * AI 사용 유형별 호출 수
   */
  public Map<String, Long> countAiCallsByType(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    List<Document> pipeline = List.of(
        new Document("$match", new Document("requestedAt", betweenDates(startDateTime, endDateTime))),
        new Document("$group", new Document("_id", "$aiUsageType").append("count", new Document("$sum", 1))));
    return aggregateCountByKey(AiUsageHistory.class, pipeline);
  }

  private Document matchLoginSuccess(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    return new Document("$match", new Document("loginAt", betweenDates(startDateTime, endDateTime))
        .append("loginResult", LOGIN_SUCCESS));
  }

  private Document toKstDay(String dateFieldPath) {
    return new Document("$dateToString",
        new Document("format", DAY_FORMAT).append("date", dateFieldPath).append("timezone", KST_TIMEZONE));
  }

  private Document betweenDates(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    return new Document("$gte", toDate(startDateTime)).append("$lte", toDate(endDateTime));
  }

  private Date toDate(LocalDateTime localDateTime) {
    return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  /**
   * 집계 실행 > {_id 문자열: count} (null 키 제외)
   */
  private Map<String, Long> aggregateCountByKey(Class<?> documentClass, List<Document> pipeline) {
    Map<String, Long> countByKey = new HashMap<>();
    for (Document resultDocument : mongoTemplate.getCollection(mongoTemplate.getCollectionName(documentClass)).aggregate(pipeline)) {
      Object groupKey = resultDocument.get("_id");
      Number count = resultDocument.get("count", Number.class);
      if (groupKey != null && count != null) {
        countByKey.merge(groupKey.toString(), count.longValue(), Long::sum);
      }
    }
    return countByKey;
  }
}
