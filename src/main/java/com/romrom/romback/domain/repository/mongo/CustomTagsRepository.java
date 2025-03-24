package com.romrom.romback.domain.repository.mongo;

import com.romrom.romback.domain.object.mongo.CustomTags;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomTagsRepository extends MongoRepository<CustomTags, UUID> {
  Optional<CustomTags> findByItemId(UUID itemId);
  void deleteByItemId(UUID itemId);
}
