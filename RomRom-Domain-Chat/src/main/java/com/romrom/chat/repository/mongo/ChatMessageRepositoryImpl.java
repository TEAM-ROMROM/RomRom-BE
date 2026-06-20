package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatMessage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public Map<UUID, Long> countUnreadMessagesByRoom(Map<UUID, LocalDateTime> readCursorByRoomId, UUID memberId) {
    Map<UUID, Long> unreadCountByRoomId = new HashMap<>();
    if (readCursorByRoomId == null || readCursorByRoomId.isEmpty()) {
      return unreadCountByRoomId;
    }

    // 방마다 읽음 커서가 다르므로, 각 방의 {방 일치 + 커서 이후} 조건을 OR로 묶어 한 번에 매칭한다.
    Criteria[] perRoomCriteria = readCursorByRoomId.entrySet().stream()
        .map(entry -> Criteria.where("chatRoomId").is(entry.getKey())
            .and("createdDate").gt(entry.getValue()))
        .toArray(Criteria[]::new);

    // 본인이 보낸 메시지는 제외하고, 방별로 그룹화해 개수를 센다.
    Aggregation unreadAggregation = Aggregation.newAggregation(
        Aggregation.match(new Criteria().orOperator(perRoomCriteria).and("senderId").ne(memberId)),
        Aggregation.group("chatRoomId").count().as("unreadCount")
    );

    AggregationResults<UnreadCountRow> aggregationResults =
        mongoTemplate.aggregate(unreadAggregation, ChatMessage.class, UnreadCountRow.class);

    for (UnreadCountRow row : aggregationResults.getMappedResults()) {
      unreadCountByRoomId.put(row.getChatRoomId(), row.getUnreadCount());
    }
    return unreadCountByRoomId;
  }

  // 그룹 결과 매핑용. 그룹 키(chatRoomId)는 _id로 매핑된다.
  @Getter
  private static class UnreadCountRow {
    @Id
    private UUID chatRoomId;
    private long unreadCount;
  }
}
