package com.romrom.item.repository.mongo;

import com.romrom.item.entity.mongo.ItemCustomTags;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemCustomTagsRepository extends MongoRepository<ItemCustomTags, UUID> {
  Optional<ItemCustomTags> findByItemId(UUID itemId);
  void deleteByItemId(UUID itemId);
}
