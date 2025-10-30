package com.romrom.item.repository.mongo;

import com.romrom.item.entity.mongo.LikeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LikeHistoryRepository extends MongoRepository<LikeHistory, String> {
  boolean existsByMemberIdAndItemId(UUID memberId, UUID itemId);
  void deleteByMemberIdAndItemId(UUID memberId, UUID itemId);
  List<LikeHistory> findByMemberId(UUID memberId);

}
