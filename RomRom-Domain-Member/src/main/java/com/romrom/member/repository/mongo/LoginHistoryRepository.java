package com.romrom.member.repository.mongo;

import com.romrom.common.constant.LoginResult;
import com.romrom.member.entity.mongo.LoginHistory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoginHistoryRepository extends MongoRepository<LoginHistory, String> {

  Page<LoginHistory> findByMemberIdOrderByLoginAtDesc(UUID memberId, Pageable pageable);

  Optional<LoginHistory> findFirstByMemberIdAndLoginResultOrderByLoginAtDesc(UUID memberId, LoginResult loginResult);

  long countByMemberId(UUID memberId);

  long countByMemberIdAndLoginResult(UUID memberId, LoginResult loginResult);
}
