package com.romrom.romback.domain.repository.mongo;

import com.romrom.romback.domain.object.mongo.LikeHistory;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeHistoryRepository extends MongoRepository<LikeHistory, String> {
  boolean existsByMemberIdAndItemId(UUID memberId, UUID itemId);
  void deleteByMemberIdAndItemId(UUID memberId, UUID itemId);
}
