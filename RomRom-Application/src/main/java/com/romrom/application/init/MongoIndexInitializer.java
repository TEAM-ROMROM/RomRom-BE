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

  /**
   * 인덱스 생성을 충돌에 견디게 감싼다.
   * - 기존에 같은 키를 다른 이름으로 만든 인덱스(@Indexed 어노테이션 등 레거시)가 있으면
   *   MongoDB가 IndexOptionsConflict(error 85)를 던지는데, 이때 앱 기동/테스트 컨텍스트가 깨지지 않도록 흡수한다.
   */
  private void ensureIndexSafely(IndexOperations indexOps, Index index, String indexLabel) {
    try {
      indexOps.ensureIndex(index);
    } catch (RuntimeException indexConflict) {
      // 동일 키의 인덱스가 이미 존재하는 경우(이름만 다름) — 기능상 동일하므로 경고만 남기고 진행
      log.warn("MongoDB 인덱스 생성 skip ({}): 이미 동일 키 인덱스 존재 또는 충돌 — {}",
          indexLabel, indexConflict.getMessage());
    }
  }

  private void ensureLoginHistoryIndexes() {
    IndexOperations loginHistoryIndexOps = mongoTemplate.indexOps(LoginHistory.class);
    ensureIndexSafely(loginHistoryIndexOps,
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("loginAt", Sort.Direction.DESC)
            .named("member_loginAt_idx"),
        "LoginHistory.member_loginAt_idx");
    ensureIndexSafely(loginHistoryIndexOps,
        new Index().on("loginAt", Sort.Direction.DESC).named("loginAt_idx"),
        "LoginHistory.loginAt_idx");
  }

  private void ensureAiUsageHistoryIndexes() {
    IndexOperations aiUsageHistoryIndexOps = mongoTemplate.indexOps(AiUsageHistory.class);
    ensureIndexSafely(aiUsageHistoryIndexOps,
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("requestedAt", Sort.Direction.DESC)
            .named("member_requestedAt_idx"),
        "AiUsageHistory.member_requestedAt_idx");
    ensureIndexSafely(aiUsageHistoryIndexOps,
        new Index()
            .on("relatedEntityId", Sort.Direction.ASC)
            .on("requestedAt", Sort.Direction.DESC)
            .named("relatedEntity_requestedAt_idx"),
        "AiUsageHistory.relatedEntity_requestedAt_idx");
    ensureIndexSafely(aiUsageHistoryIndexOps,
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("aiUsageType", Sort.Direction.ASC)
            .named("member_aiUsageType_idx"),
        "AiUsageHistory.member_aiUsageType_idx");
  }

  private void ensureSanctionHistoryIndexes() {
    IndexOperations sanctionHistoryIndexOps = mongoTemplate.indexOps(SanctionHistory.class);
    ensureIndexSafely(sanctionHistoryIndexOps,
        new Index()
            .on("memberId", Sort.Direction.ASC)
            .on("suspendedAt", Sort.Direction.DESC)
            .named("member_suspendedAt_idx"),
        "SanctionHistory.member_suspendedAt_idx");
    ensureIndexSafely(sanctionHistoryIndexOps,
        new Index().on("executorAdminId", Sort.Direction.ASC).named("executorAdminId_idx"),
        "SanctionHistory.executorAdminId_idx");
  }
}
