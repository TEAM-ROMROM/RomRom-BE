package com.romrom.common.repository;

import com.romrom.common.entity.mongo.UserActivityEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActivityEventRepository extends MongoRepository<UserActivityEvent, String> {
}
