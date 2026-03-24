package com.romrom.member.repository.mongo;

import com.romrom.member.entity.mongo.SanctionHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SanctionHistoryRepository extends MongoRepository<SanctionHistory, String> {

  List<SanctionHistory> findByMemberIdOrderBySuspendedAtDesc(UUID memberId);

  Optional<SanctionHistory> findFirstByMemberIdAndLiftedAtIsNullOrderBySuspendedAtDesc(UUID memberId);

  Page<SanctionHistory> findAllByOrderBySuspendedAtDesc(Pageable pageable);

  Page<SanctionHistory> findByMemberIdOrderBySuspendedAtDesc(UUID memberId, Pageable pageable);

  void deleteAllByMemberId(UUID memberId);
}
