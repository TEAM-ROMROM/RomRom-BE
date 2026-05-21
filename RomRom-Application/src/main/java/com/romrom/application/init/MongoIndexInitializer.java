package com.romrom.application.init;

import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.member.entity.mongo.LoginHistory;
import com.romrom.member.entity.mongo.SanctionHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class MongoIndexInitializer {

  private final MongoTemplate mongoTemplate;

  @EventListener(ApplicationReadyEvent.class)
  public void ensureAdminMember360Indexes() {
    ensureLoginHistoryIndexes();
    ensureAiUsageHistoryIndexes();
    ensureSanctionHistoryIndexes();
    log.info("Admin Member 360 MongoDB 인덱스 보장 완료");
  }

  private void ensureLoginHistoryIndexes() {
    IndexOperations loginHistoryIndexOps = mongoTemplate.indexOps(LoginHistory.class);
    loginHistoryIndexOps.ensureIndex(
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("loginAt", Sort.Direction.DESC)
            .named("member_loginAt_idx")
    );
    loginHistoryIndexOps.ensureIndex(
        new Index().on("loginAt", Sort.Direction.DESC).named("loginAt_idx")
    );
  }

  private void ensureAiUsageHistoryIndexes() {
    IndexOperations aiUsageHistoryIndexOps = mongoTemplate.indexOps(AiUsageHistory.class);
    aiUsageHistoryIndexOps.ensureIndex(
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("requestedAt", Sort.Direction.DESC)
            .named("member_requestedAt_idx")
    );
    aiUsageHistoryIndexOps.ensureIndex(
        new Index()
            .on("relatedEntityId", Sort.Direction.ASC)
            .on("requestedAt", Sort.Direction.DESC)
            .named("relatedEntity_requestedAt_idx")
    );
    aiUsageHistoryIndexOps.ensureIndex(
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("aiUsageType", Sort.Direction.ASC)
            .named("member_aiUsageType_idx")
    );
  }

  private void ensureSanctionHistoryIndexes() {
    IndexOperations sanctionHistoryIndexOps = mongoTemplate.indexOps(SanctionHistory.class);
    sanctionHistoryIndexOps.ensureIndex(
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("suspendedAt", Sort.Direction.DESC)
            .named("member_suspendedAt_idx")
    );
    sanctionHistoryIndexOps.ensureIndex(
        new Index().on("executorAdminId", Sort.Direction.ASC).named("executorAdminId_idx")
    );
  }
}
