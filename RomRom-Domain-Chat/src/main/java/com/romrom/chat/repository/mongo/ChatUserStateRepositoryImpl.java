package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatUserState;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatUserStateRepositoryImpl implements ChatUserStateRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public ChatUserState markRemovedIfNotRemoved(UUID chatRoomId, UUID memberId) {
    Query removableStateQuery = new Query(
        Criteria.where("chatRoomId").is(chatRoomId)
            .and("memberId").is(memberId)
            .and("removedAt").is(null)
    );
    Update markRemovedUpdate = new Update().set("removedAt", LocalDateTime.now());
    FindAndModifyOptions returnUpdatedDocument = FindAndModifyOptions.options().returnNew(true);

    return mongoTemplate.findAndModify(
        removableStateQuery, markRemovedUpdate, returnUpdatedDocument, ChatUserState.class);
  }

  @Override
  public long countOnlineChatMembers() {
    // leftAt == null 인 상태(현재 접속 중)의 memberId를 distinct로 뽑아 고유 회원 수를 센다.
    // 한 회원이 여러 방에 접속해 있어도 1명으로 집계되어야 하므로 단순 count가 아닌 distinct 사용.
    Query onlineChatStateQuery = new Query(Criteria.where("leftAt").is(null));
    return mongoTemplate.findDistinct(
        onlineChatStateQuery, "memberId", ChatUserState.class, UUID.class).size();
  }
}
