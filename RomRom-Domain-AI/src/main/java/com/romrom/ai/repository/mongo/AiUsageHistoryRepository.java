package com.romrom.ai.repository.mongo;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.common.constant.AiUsageType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AiUsageHistoryRepository extends MongoRepository<AiUsageHistory, String> {

  Page<AiUsageHistory> findByMemberIdOrderByRequestedAtDesc(UUID memberId, Pageable pageable);

  Page<AiUsageHistory> findByMemberIdAndAiUsageTypeOrderByRequestedAtDesc(UUID memberId, AiUsageType aiUsageType, Pageable pageable);

  Page<AiUsageHistory> findByRelatedEntityIdOrderByRequestedAtDesc(UUID relatedEntityId, Pageable pageable);

  long countByMemberId(UUID memberId);

  long countByMemberIdAndAiUsageType(UUID memberId, AiUsageType aiUsageType);
}
