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
}
