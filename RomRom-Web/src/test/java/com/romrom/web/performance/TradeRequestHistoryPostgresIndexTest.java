package com.romrom.web.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("postgresIndexTestEnabled")
class TradeRequestHistoryPostgresIndexTest {

  /*
   * Web 모듈에 둔 이유
   * 1. 실제 실행 애플리케이션 모듈이 PostgreSQL 드라이버와 Flyway 마이그레이션 리소스를 가졌음
   * 2. 이 테스트는 Item 도메인 단위 테스트가 아니라 운영 DB 인덱스 전략을 검증하는 Postgres 전용 테스트임
   * 3. 전체 테스트에서는 무겁게 돌지 않고, 클래스 직접 실행/환경변수/시스템 프로퍼티로만 활성화하기 위함
   * 4. public 운영 테이블/인덱스를 건드리지 않도록 매 실행마다 전용 스키마를 생성/삭제함
   */
  private static final int MOCK_ROW_COUNT = Integer.getInteger("romrom.postgres.index-test.rows", 200_000);
  private static final int HOT_ROW_COUNT = Integer.getInteger("romrom.postgres.index-test.hot-rows", 2_000);
  private static final int MEASURE_REPETITIONS = Integer.getInteger("romrom.postgres.index-test.repetitions", 8);
  private static final int CONCURRENT_REQUESTS = Integer.getInteger("romrom.postgres.index-test.concurrent-requests", 32);

  private static boolean postgresIndexTestEnabled() {
    return Boolean.getBoolean("romrom.postgres.index-test.enabled")
        || "true".equalsIgnoreCase(System.getenv("ROMROM_POSTGRES_INDEX_TEST_ENABLED"));
  }

  @Test
  void isolatedTradeRequestTableBlocksConcurrentDuplicateActiveRequests() throws Exception {
    String schemaName = createIsolatedSchemaName();
    UUID memberA = UUID.randomUUID();
    UUID memberB = UUID.randomUUID();
    UUID itemA = UUID.randomUUID();
    UUID itemB = UUID.randomUUID();
    String runId = UUID.randomUUID().toString();
    AtomicInteger duplicateKeyCount = new AtomicInteger();

    try {
      try (Connection connection = connect()) {
        createIsolatedSchema(connection, schemaName);
        createMigrationIndexes(connection, schemaName);
        insertMember(connection, schemaName, memberA, "rr-concurrency-a-" + runId);
        insertMember(connection, schemaName, memberB, "rr-concurrency-b-" + runId);
        insertItem(connection, schemaName, itemA, memberA, "rr-concurrency-take-" + runId);
        insertItem(connection, schemaName, itemB, memberB, "rr-concurrency-give-" + runId);
      }

      int successCount = 0;
      ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
      CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
      CountDownLatch startLatch = new CountDownLatch(1);
      List<Future<Boolean>> futures = new ArrayList<>();
      try {
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
          // A->B와 B->A를 섞어 같은 물품쌍 정규화 unique index를 검증한다.
          boolean reversePair = i % 2 == 0;
          futures.add(executorService.submit(insertTradeRequestTask(
              schemaName, itemA, itemB, reversePair, readyLatch, startLatch, duplicateKeyCount
          )));
        }

        assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();

        for (Future<Boolean> future : futures) {
          if (future.get(10, TimeUnit.SECONDS)) {
            successCount++;
          }
        }
      } finally {
        startLatch.countDown();
        executorService.shutdownNow();
        assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      }

      long activeRowCount;
      long canceledRowCount;
      try (Connection connection = connect()) {
        activeRowCount = countTradeRequestsForPair(connection, schemaName, itemA, itemB, "trade_status IN (0, 1, 3, 4)");
        assertThat(activeRowCount).isEqualTo(1L);

        // CANCELED는 partial unique index 대상이 아니므로 같은 물품쌍 재요청을 막지 않는다.
        insertTradeRequest(connection, schemaName, itemA, itemB, 2);
        insertTradeRequest(connection, schemaName, itemB, itemA, 2);
        canceledRowCount = countTradeRequestsForPair(connection, schemaName, itemA, itemB, "trade_status = 2");
        assertThat(canceledRowCount).isEqualTo(2L);
      }

      printConcurrencyLog(successCount, duplicateKeyCount.get(), activeRowCount, canceledRowCount);

      assertThat(successCount).isEqualTo(1);
      assertThat(duplicateKeyCount.get()).isEqualTo(CONCURRENT_REQUESTS - 1);
    } finally {
      dropIsolatedSchema(schemaName);
    }
  }

  @Test
  void compareLookupPerformanceOnIsolatedItemAndTradeRequestTables() throws Exception {
    validateMockCounts();

    String schemaName = createIsolatedSchemaName();
    UUID memberA = UUID.randomUUID();
    UUID memberB = UUID.randomUUID();
    UUID targetTakeItemId = UUID.randomUUID();
    UUID targetGiveItemId = UUID.randomUUID();
    UUID hotTakeItemId = UUID.randomUUID();
    UUID hotGiveItemId = UUID.randomUUID();
    String runId = UUID.randomUUID().toString();
    String itemIdPrefix = "rr-perf-item-" + runId + "-";
    String tradeIdPrefix = "rr-perf-trade-" + runId + "-";
    String hotReceivedTradeIdPrefix = "rr-perf-hot-received-trade-" + runId + "-";
    String hotSentTradeIdPrefix = "rr-perf-hot-sent-trade-" + runId + "-";

    PerformanceResult noIndexResult;
    PerformanceResult indexedResult;
    PlanResult noIndexPlan;
    PlanResult indexedPlan;

    try {
      try (Connection connection = connect()) {
        createIsolatedSchema(connection, schemaName);
        seedPerformanceRows(
            connection,
            schemaName,
            memberA,
            memberB,
            targetTakeItemId,
            targetGiveItemId,
            hotTakeItemId,
            hotGiveItemId,
            itemIdPrefix,
            tradeIdPrefix,
            hotReceivedTradeIdPrefix,
            hotSentTradeIdPrefix,
            runId
        );

        noIndexPlan = explainPlans(connection, schemaName, targetTakeItemId, targetGiveItemId, hotTakeItemId, hotGiveItemId);
        noIndexResult = measurePerformance(connection, schemaName, targetTakeItemId, targetGiveItemId, hotTakeItemId, hotGiveItemId);

        createMigrationIndexes(connection, schemaName);
        analyzeTables(connection, schemaName);

        indexedPlan = explainPlans(connection, schemaName, targetTakeItemId, targetGiveItemId, hotTakeItemId, hotGiveItemId);
        indexedResult = measurePerformance(connection, schemaName, targetTakeItemId, targetGiveItemId, hotTakeItemId, hotGiveItemId);
      }

      assertNoTradeRequestHistoryIndexUsed(noIndexPlan);
      assertTargetIndexesUsed(indexedPlan);
      printPerformanceLog(noIndexResult, indexedResult, noIndexPlan, indexedPlan);
    } finally {
      dropIsolatedSchema(schemaName);
    }
  }

  private Callable<Boolean> insertTradeRequestTask(
      String schemaName,
      UUID itemA,
      UUID itemB,
      boolean reversePair,
      CountDownLatch readyLatch,
      CountDownLatch startLatch,
      AtomicInteger duplicateKeyCount
  ) {
    return () -> {
      UUID takeItemId = reversePair ? itemB : itemA;
      UUID giveItemId = reversePair ? itemA : itemB;
      readyLatch.countDown();
      // 모든 스레드를 같은 출발선에 세워 exists -> save 사이의 경쟁 상황을 재현한다.
      startLatch.await();

      try (Connection connection = connect()) {
        insertTradeRequest(connection, schemaName, takeItemId, giveItemId, 0);
        return true;
      } catch (SQLException e) {
        // PostgreSQL unique_violation. 한 요청만 성공하고 나머지는 이 경로로 들어와야 한다.
        if ("23505".equals(e.getSQLState())) {
          duplicateKeyCount.incrementAndGet();
          return false;
        }
        throw e;
      }
    };
  }

  private PerformanceResult measurePerformance(
      Connection connection,
      String schemaName,
      UUID targetTakeItemId,
      UUID targetGiveItemId,
      UUID hotTakeItemId,
      UUID hotGiveItemId
  ) throws SQLException {
    double activePairLookupAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeActivePairLookup(connection, schemaName, targetTakeItemId, targetGiveItemId)
    );
    double receivedListLimitAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeReceivedListLookup(connection, schemaName, hotTakeItemId, true)
    );
    double sentListLimitAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeSentListLookup(connection, schemaName, hotGiveItemId, true)
    );
    double receivedListNoLimitAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeReceivedListLookup(connection, schemaName, hotTakeItemId, false)
    );
    double sentListNoLimitAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeSentListLookup(connection, schemaName, hotGiveItemId, false)
    );
    return new PerformanceResult(
        activePairLookupAverageMs,
        receivedListLimitAverageMs,
        sentListLimitAverageMs,
        receivedListNoLimitAverageMs,
        sentListNoLimitAverageMs
    );
  }

  private PlanResult explainPlans(
      Connection connection,
      String schemaName,
      UUID targetTakeItemId,
      UUID targetGiveItemId,
      UUID hotTakeItemId,
      UUID hotGiveItemId
  ) throws SQLException {
    return new PlanResult(
        explain(connection, activePairLookupSql(schemaName), statement -> bindItemPair(statement, targetTakeItemId, targetGiveItemId)),
        explain(connection, receivedListLookupSql(schemaName, true), statement -> statement.setObject(1, hotTakeItemId)),
        explain(connection, sentListLookupSql(schemaName, true), statement -> statement.setObject(1, hotGiveItemId)),
        explain(connection, receivedListLookupSql(schemaName, false), statement -> statement.setObject(1, hotTakeItemId)),
        explain(connection, sentListLookupSql(schemaName, false), statement -> statement.setObject(1, hotGiveItemId))
    );
  }

  private void executeActivePairLookup(Connection connection, String schemaName, UUID takeItemId, UUID giveItemId) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement(activePairLookupSql(schemaName))) {
      bindItemPair(preparedStatement, takeItemId, giveItemId);
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getBoolean(1)).isTrue();
      }
    }
  }

  private void executeReceivedListLookup(
      Connection connection,
      String schemaName,
      UUID hotTakeItemId,
      boolean firstPageOnly
  ) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement(receivedListLookupSql(schemaName, firstPageOnly))) {
      preparedStatement.setObject(1, hotTakeItemId);
      assertThat(countRows(preparedStatement)).isEqualTo(expectedListRowCount(firstPageOnly));
    }
  }

  private void executeSentListLookup(
      Connection connection,
      String schemaName,
      UUID hotGiveItemId,
      boolean firstPageOnly
  ) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement(sentListLookupSql(schemaName, firstPageOnly))) {
      preparedStatement.setObject(1, hotGiveItemId);
      assertThat(countRows(preparedStatement)).isEqualTo(expectedListRowCount(firstPageOnly));
    }
  }

  private String activePairLookupSql(String schemaName) {
    return """
        SELECT EXISTS (
            SELECT 1
            FROM %s t
            WHERE t.trade_status IN (0, 1, 3, 4)
              AND LEAST(t.take_item_item_id, t.give_item_item_id) = LEAST(CAST(? AS uuid), CAST(? AS uuid))
              AND GREATEST(t.take_item_item_id, t.give_item_item_id) = GREATEST(CAST(? AS uuid), CAST(? AS uuid))
        )
        """.formatted(table(schemaName, "trade_request_history"));
  }

  private String receivedListLookupSql(String schemaName, boolean firstPageOnly) {
    return """
        SELECT t.trade_request_history_id
        FROM %s t
        JOIN %s take_i ON take_i.item_id = t.take_item_item_id
        JOIN %s give_i ON give_i.item_id = t.give_item_item_id
        WHERE t.take_item_item_id = ?
          AND take_i.item_status = 'AVAILABLE'
          AND t.trade_status IN (0, 1, 3, 4)
          AND NOT EXISTS (
              SELECT 1
              FROM %s mb
              WHERE (mb.blocker_member_id = take_i.member_member_id AND mb.blocked_member_id = give_i.member_member_id)
                 OR (mb.blocker_member_id = give_i.member_member_id AND mb.blocked_member_id = take_i.member_member_id)
          )
        ORDER BY t.created_date DESC
        %s
        """.formatted(
        table(schemaName, "trade_request_history"),
        table(schemaName, "item"),
        table(schemaName, "item"),
        table(schemaName, "member_block"),
        firstPageOnly ? "LIMIT 20" : ""
    );
  }

  private String sentListLookupSql(String schemaName, boolean firstPageOnly) {
    return """
        SELECT t.trade_request_history_id
        FROM %s t
        JOIN %s give_i ON give_i.item_id = t.give_item_item_id
        JOIN %s take_i ON take_i.item_id = t.take_item_item_id
        WHERE t.give_item_item_id = ?
          AND give_i.item_status = 'AVAILABLE'
          AND t.trade_status IN (0, 1, 3, 4)
          AND NOT EXISTS (
              SELECT 1
              FROM %s mb
              WHERE (mb.blocker_member_id = give_i.member_member_id AND mb.blocked_member_id = take_i.member_member_id)
                 OR (mb.blocker_member_id = take_i.member_member_id AND mb.blocked_member_id = give_i.member_member_id)
          )
        ORDER BY t.created_date DESC
        %s
        """.formatted(
        table(schemaName, "trade_request_history"),
        table(schemaName, "item"),
        table(schemaName, "item"),
        table(schemaName, "member_block"),
        firstPageOnly ? "LIMIT 20" : ""
    );
  }

  private void bindItemPair(PreparedStatement preparedStatement, UUID takeItemId, UUID giveItemId) throws SQLException {
    preparedStatement.setObject(1, takeItemId);
    preparedStatement.setObject(2, giveItemId);
    preparedStatement.setObject(3, takeItemId);
    preparedStatement.setObject(4, giveItemId);
  }

  private int countRows(PreparedStatement preparedStatement) throws SQLException {
    int rowCount = 0;
    try (ResultSet resultSet = preparedStatement.executeQuery()) {
      while (resultSet.next()) {
        rowCount++;
      }
    }
    return rowCount;
  }

  private String explain(Connection connection, String sql, SqlParameterBinder binder) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("EXPLAIN (ANALYZE, FORMAT TEXT) " + sql)) {
      binder.bind(preparedStatement);
      StringBuilder plan = new StringBuilder();
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        while (resultSet.next()) {
          plan.append(resultSet.getString(1)).append('\n');
        }
      }
      return plan.toString();
    }
  }

  private double averageMs(int repetitions, SqlRunnable runnable) throws SQLException {
    // 쿼리 플랜/캐시 워밍업을 간단히 거친 뒤 평균 ms를 계산한다. 시간은 로그용이며 테스트 성공 조건으로 사용하지 않는다.
    for (int i = 0; i < 3; i++) {
      runnable.run();
    }

    long totalNanos = 0;
    for (int i = 0; i < repetitions; i++) {
      long startNanos = System.nanoTime();
      runnable.run();
      totalNanos += System.nanoTime() - startNanos;
    }
    return totalNanos / 1_000_000.0 / repetitions;
  }

  private void seedPerformanceRows(
      Connection connection,
      String schemaName,
      UUID memberA,
      UUID memberB,
      UUID targetTakeItemId,
      UUID targetGiveItemId,
      UUID hotTakeItemId,
      UUID hotGiveItemId,
      String itemIdPrefix,
      String tradeIdPrefix,
      String hotReceivedTradeIdPrefix,
      String hotSentTradeIdPrefix,
      String runId
  ) throws SQLException {
    insertMember(connection, schemaName, memberA, "rr-perf-a-" + runId);
    insertMember(connection, schemaName, memberB, "rr-perf-b-" + runId);
    insertItem(connection, schemaName, targetTakeItemId, memberA, "rr-perf-target-take-" + runId);
    insertItem(connection, schemaName, targetGiveItemId, memberB, "rr-perf-target-give-" + runId);
    insertItem(connection, schemaName, hotTakeItemId, memberA, "rr-perf-hot-take-" + runId);
    insertItem(connection, schemaName, hotGiveItemId, memberB, "rr-perf-hot-give-" + runId);
    insertPerformanceItems(connection, schemaName, memberA, memberB, itemIdPrefix);
    insertBaseTradeRequests(connection, schemaName, itemIdPrefix, tradeIdPrefix);
    insertHotReceivedTradeRequests(connection, schemaName, hotTakeItemId, itemIdPrefix, hotReceivedTradeIdPrefix);
    insertHotSentTradeRequests(connection, schemaName, hotGiveItemId, itemIdPrefix, hotSentTradeIdPrefix);
    insertTradeRequest(connection, schemaName, targetTakeItemId, targetGiveItemId, 0);
    analyzeTables(connection, schemaName);
  }

  private void insertPerformanceItems(Connection connection, String schemaName, UUID memberA, UUID memberB, String itemIdPrefix) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            item_id,
            member_member_id,
            item_name,
            item_description,
            item_category,
            item_condition,
            item_status,
            like_count,
            price,
            is_ai_predicted_price,
            is_deleted,
            created_date,
            updated_date
        )
        SELECT
            md5(? || g::text)::uuid,
            CASE WHEN g %% 2 = 0 THEN CAST(? AS uuid) ELSE CAST(? AS uuid) END,
            'rr-perf-item-' || g::text,
            'trade request index performance mock item',
            ((g %% 25) + 1)::integer,
            'SLIGHTLY_USED',
            'AVAILABLE',
            0,
            1000,
            false,
            false,
            now(),
            now()
        FROM generate_series(1, ?) AS g
        """.formatted(table(schemaName, "item")))) {
      preparedStatement.setString(1, itemIdPrefix);
      preparedStatement.setObject(2, memberA);
      preparedStatement.setObject(3, memberB);
      preparedStatement.setInt(4, MOCK_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertBaseTradeRequests(Connection connection, String schemaName, String itemIdPrefix, String tradeIdPrefix) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            trade_request_history_id,
            take_item_item_id,
            give_item_item_id,
            trade_status,
            is_new,
            created_date,
            updated_date
        )
        SELECT
            md5(? || g::text)::uuid,
            md5(? || g::text)::uuid,
            md5(? || (CASE WHEN g = ? THEN 1 ELSE g + 1 END)::text)::uuid,
            CASE g %% 5
                WHEN 0 THEN 2
                WHEN 1 THEN 0
                WHEN 2 THEN 1
                WHEN 3 THEN 3
                ELSE 0
            END,
            true,
            now() - (g * interval '1 second'),
            now() - (g * interval '1 second')
        FROM generate_series(1, ?) AS g
        """.formatted(table(schemaName, "trade_request_history")))) {
      preparedStatement.setString(1, tradeIdPrefix);
      preparedStatement.setString(2, itemIdPrefix);
      preparedStatement.setString(3, itemIdPrefix);
      preparedStatement.setInt(4, MOCK_ROW_COUNT);
      preparedStatement.setInt(5, MOCK_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertHotReceivedTradeRequests(
      Connection connection,
      String schemaName,
      UUID hotTakeItemId,
      String itemIdPrefix,
      String hotTradeIdPrefix
  ) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            trade_request_history_id,
            take_item_item_id,
            give_item_item_id,
            trade_status,
            is_new,
            created_date,
            updated_date
        )
        SELECT
            md5(? || g::text)::uuid,
            CAST(? AS uuid),
            md5(? || g::text)::uuid,
            CASE WHEN g %% 7 = 0 THEN 2 ELSE 0 END,
            true,
            now() - (g * interval '1 second'),
            now() - (g * interval '1 second')
        FROM generate_series(1, ?) AS g
        """.formatted(table(schemaName, "trade_request_history")))) {
      preparedStatement.setString(1, hotTradeIdPrefix);
      preparedStatement.setObject(2, hotTakeItemId);
      preparedStatement.setString(3, itemIdPrefix);
      preparedStatement.setInt(4, HOT_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertHotSentTradeRequests(
      Connection connection,
      String schemaName,
      UUID hotGiveItemId,
      String itemIdPrefix,
      String hotTradeIdPrefix
  ) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            trade_request_history_id,
            take_item_item_id,
            give_item_item_id,
            trade_status,
            is_new,
            created_date,
            updated_date
        )
        SELECT
            md5(? || g::text)::uuid,
            md5(? || g::text)::uuid,
            CAST(? AS uuid),
            CASE WHEN g %% 7 = 0 THEN 2 ELSE 0 END,
            true,
            now() - (g * interval '1 second'),
            now() - (g * interval '1 second')
        FROM generate_series(1, ?) AS g
        """.formatted(table(schemaName, "trade_request_history")))) {
      preparedStatement.setString(1, hotTradeIdPrefix);
      preparedStatement.setString(2, itemIdPrefix);
      preparedStatement.setObject(3, hotGiveItemId);
      preparedStatement.setInt(4, HOT_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertMember(Connection connection, String schemaName, UUID memberId, String marker) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            member_id,
            email,
            nickname,
            social_platform,
            role,
            account_status,
            profile_url,
            is_first_login,
            is_item_category_saved,
            is_first_item_posted,
            is_member_location_saved,
            is_required_terms_agreed,
            is_marketing_info_agreed,
            is_activity_notification_agreed,
            is_chat_notification_agreed,
            is_content_notification_agreed,
            is_trade_notification_agreed,
            is_deleted,
            total_like_count,
            created_date,
            updated_date
        )
        VALUES (?, ?, ?, 'KAKAO', 'ROLE_USER', 'TEST_ACCOUNT', '', false, true, true, true, true, false, false, false, false, false, false, 0, now(), now())
        """.formatted(table(schemaName, "member")))) {
      preparedStatement.setObject(1, memberId);
      preparedStatement.setString(2, marker + "@romrom.test");
      preparedStatement.setString(3, marker);
      preparedStatement.executeUpdate();
    }
  }

  private void insertItem(Connection connection, String schemaName, UUID itemId, UUID memberId, String itemName) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            item_id,
            member_member_id,
            item_name,
            item_description,
            item_category,
            item_condition,
            item_status,
            like_count,
            price,
            is_ai_predicted_price,
            is_deleted,
            created_date,
            updated_date
        )
        VALUES (?, ?, ?, 'trade request index performance mock item', 25, 'SLIGHTLY_USED', 'AVAILABLE', 0, 1000, false, false, now(), now())
        """.formatted(table(schemaName, "item")))) {
      preparedStatement.setObject(1, itemId);
      preparedStatement.setObject(2, memberId);
      preparedStatement.setString(3, itemName);
      preparedStatement.executeUpdate();
    }
  }

  private void insertTradeRequest(Connection connection, String schemaName, UUID takeItemId, UUID giveItemId, int tradeStatus) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO %s (
            trade_request_history_id,
            take_item_item_id,
            give_item_item_id,
            trade_status,
            is_new,
            created_date,
            updated_date
        )
        VALUES (?, ?, ?, ?, true, ?, ?)
        """.formatted(table(schemaName, "trade_request_history")))) {
      LocalDateTime now = LocalDateTime.now();
      preparedStatement.setObject(1, UUID.randomUUID());
      preparedStatement.setObject(2, takeItemId);
      preparedStatement.setObject(3, giveItemId);
      preparedStatement.setInt(4, tradeStatus);
      preparedStatement.setObject(5, now);
      preparedStatement.setObject(6, now);
      preparedStatement.executeUpdate();
    }
  }

  private long countTradeRequestsForPair(
      Connection connection,
      String schemaName,
      UUID itemA,
      UUID itemB,
      String statusWhereClause
  ) throws SQLException {
    String sql = """
        SELECT COUNT(*)
        FROM %s
        WHERE %s
          AND LEAST(take_item_item_id, give_item_item_id) = LEAST(CAST(? AS uuid), CAST(? AS uuid))
          AND GREATEST(take_item_item_id, give_item_item_id) = GREATEST(CAST(? AS uuid), CAST(? AS uuid))
        """.formatted(table(schemaName, "trade_request_history"), statusWhereClause);
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setObject(1, itemA);
      preparedStatement.setObject(2, itemB);
      preparedStatement.setObject(3, itemA);
      preparedStatement.setObject(4, itemB);
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        return resultSet.getLong(1);
      }
    }
  }

  private void createIsolatedSchema(Connection connection, String schemaName) throws SQLException {
    execute(connection, "CREATE SCHEMA " + schemaName);
    execute(connection, """
        CREATE TABLE %s (
            member_id uuid PRIMARY KEY,
            email varchar UNIQUE,
            nickname varchar UNIQUE,
            social_platform varchar,
            role varchar,
            account_status varchar,
            profile_url varchar,
            is_first_login boolean NOT NULL,
            is_item_category_saved boolean NOT NULL,
            is_first_item_posted boolean NOT NULL,
            is_member_location_saved boolean NOT NULL,
            is_required_terms_agreed boolean NOT NULL,
            is_marketing_info_agreed boolean NOT NULL,
            is_activity_notification_agreed boolean NOT NULL,
            is_chat_notification_agreed boolean NOT NULL,
            is_content_notification_agreed boolean NOT NULL,
            is_trade_notification_agreed boolean NOT NULL,
            is_deleted boolean NOT NULL,
            total_like_count integer NOT NULL,
            created_date timestamp NOT NULL,
            updated_date timestamp NOT NULL
        )
        """.formatted(table(schemaName, "member")));
    execute(connection, """
        CREATE TABLE %s (
            item_id uuid PRIMARY KEY,
            member_member_id uuid REFERENCES %s(member_id),
            item_name varchar NOT NULL,
            item_description varchar,
            item_category integer,
            item_condition varchar,
            item_status varchar,
            like_count integer,
            price integer,
            is_ai_predicted_price boolean NOT NULL,
            is_deleted boolean NOT NULL,
            created_date timestamp NOT NULL,
            updated_date timestamp NOT NULL
        )
        """.formatted(table(schemaName, "item"), table(schemaName, "member")));
    execute(connection, """
        CREATE TABLE %s (
            member_block_id uuid PRIMARY KEY,
            blocker_member_id uuid REFERENCES %s(member_id),
            blocked_member_id uuid REFERENCES %s(member_id),
            created_date timestamp NOT NULL DEFAULT now(),
            updated_date timestamp NOT NULL DEFAULT now()
        )
        """.formatted(table(schemaName, "member_block"), table(schemaName, "member"), table(schemaName, "member")));
    execute(connection, """
        CREATE TABLE %s (
            trade_request_history_id uuid PRIMARY KEY,
            take_item_item_id uuid REFERENCES %s(item_id),
            give_item_item_id uuid REFERENCES %s(item_id),
            trade_status smallint NOT NULL,
            is_new boolean NOT NULL,
            created_date timestamp NOT NULL,
            updated_date timestamp NOT NULL
        )
        """.formatted(table(schemaName, "trade_request_history"), table(schemaName, "item"), table(schemaName, "item")));
  }

  private void createMigrationIndexes(Connection connection, String schemaName) throws SQLException {
    execute(connection, """
        CREATE UNIQUE INDEX uq_trh_active_item_pair
            ON %s (
                LEAST(take_item_item_id, give_item_item_id),
                GREATEST(take_item_item_id, give_item_item_id)
            )
            WHERE trade_status IN (0, 1, 3, 4)
        """.formatted(table(schemaName, "trade_request_history")));
    execute(connection, """
        CREATE INDEX idx_trh_take_active_created_date
            ON %s (take_item_item_id, created_date DESC)
            WHERE trade_status IN (0, 1, 3, 4)
        """.formatted(table(schemaName, "trade_request_history")));
    execute(connection, """
        CREATE INDEX idx_trh_give_active_created_date
            ON %s (give_item_item_id, created_date DESC)
            WHERE trade_status IN (0, 1, 3, 4)
        """.formatted(table(schemaName, "trade_request_history")));
  }

  private void analyzeTables(Connection connection, String schemaName) throws SQLException {
    execute(connection, "ANALYZE " + table(schemaName, "member"));
    execute(connection, "ANALYZE " + table(schemaName, "item"));
    execute(connection, "ANALYZE " + table(schemaName, "member_block"));
    execute(connection, "ANALYZE " + table(schemaName, "trade_request_history"));
  }

  private void dropIsolatedSchema(String schemaName) throws SQLException {
    try (Connection connection = connect()) {
      execute(connection, "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
    }
  }

  private void assertNoTradeRequestHistoryIndexUsed(PlanResult noIndexPlan) {
    assertThat(noIndexPlan.combined()).doesNotContain("uq_trh_active_item_pair");
    assertThat(noIndexPlan.combined()).doesNotContain("idx_trh_take_active_created_date");
    assertThat(noIndexPlan.combined()).doesNotContain("idx_trh_give_active_created_date");
    assertThat(noIndexPlan.activePairLookup()).contains("Seq Scan on trade_request_history");
    assertThat(noIndexPlan.receivedListLimit()).contains("Seq Scan on trade_request_history");
    assertThat(noIndexPlan.sentListLimit()).contains("Seq Scan on trade_request_history");
  }

  private void assertTargetIndexesUsed(PlanResult indexedPlan) {
    assertThat(indexedPlan.activePairLookup()).contains("uq_trh_active_item_pair");
    assertThat(indexedPlan.receivedListLimit()).contains("idx_trh_take_active_created_date");
    assertThat(indexedPlan.sentListLimit()).contains("idx_trh_give_active_created_date");
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(
        requiredPropertyOrEnv("romrom.test.postgres.url", "ROMROM_TEST_POSTGRES_URL"),
        requiredPropertyOrEnv("romrom.test.postgres.username", "ROMROM_TEST_POSTGRES_USERNAME"),
        requiredPropertyOrEnv("romrom.test.postgres.password", "ROMROM_TEST_POSTGRES_PASSWORD")
    );
  }

  private String requiredPropertyOrEnv(String propertyName, String envName) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    throw new IllegalStateException("PostgreSQL 성능 테스트 실행을 위해 " + propertyName + " 또는 " + envName + " 값을 설정해야 합니다.");
  }

  private void validateMockCounts() {
    assertThat(MOCK_ROW_COUNT).isGreaterThanOrEqualTo(3);
    assertThat(HOT_ROW_COUNT).isGreaterThanOrEqualTo(24);
    assertThat(HOT_ROW_COUNT).isLessThanOrEqualTo(MOCK_ROW_COUNT);
  }

  private int expectedActiveHotTradeRequestCount() {
    return HOT_ROW_COUNT - (HOT_ROW_COUNT / 7);
  }

  private int expectedListRowCount(boolean firstPageOnly) {
    return firstPageOnly ? 20 : expectedActiveHotTradeRequestCount();
  }

  private String createIsolatedSchemaName() {
    return "romrom_perf_" + UUID.randomUUID().toString().replace("-", "_");
  }

  private String table(String schemaName, String tableName) {
    return schemaName + "." + tableName;
  }

  private void printConcurrencyLog(
      int successCount,
      int duplicateKeyCount,
      long activeRowCount,
      long canceledRowCount
  ) {
    System.out.printf("""
        [TradeRequestHistoryPostgresIndexTest] 격리 스키마 동시성 테스트 결과
        - 실제 member 형태 목데이터: 2건
        - 실제 item 형태 목데이터: 2건
        - 동시 거래요청 insert 수: %,d
        - insert 성공 수: %,d
        - unique 충돌 수: %,d
        - 활성 거래요청 row 수: %,d
        - 취소 거래요청 row 수: %,d
        - 결론: 같은 물품쌍 활성 거래요청은 partial unique index로 1건만 허용됨
        %n""",
        CONCURRENT_REQUESTS,
        successCount,
        duplicateKeyCount,
        activeRowCount,
        canceledRowCount
    );
  }

  private void printPerformanceLog(PerformanceResult noIndexResult, PerformanceResult indexedResult, PlanResult noIndexPlan, PlanResult indexedPlan) {
    System.out.printf("""
        [TradeRequestHistoryPostgresIndexTest] 격리 스키마 거래요청 API 성능 테스트 결과
        - 실제 member 형태 목데이터: 2건
        - 실제 item 형태 목데이터: %,d건
        - 실제 trade_request_history 형태 목데이터: %,d건
        - 반복 측정 횟수: %,d
        - EXPLAIN 확인: 인덱스 전 Seq Scan 포함=%s, 인덱스 후 대상 인덱스 사용=%s
        - 중복 요청 체크 API 조회(/api/trade/check, /api/trade/post 사전 검증): %.3fms -> %.3fms, %.2fx 개선
        - 내 물건에 요청한 거래요청 목록 API(/api/trade/get/received, LIMIT 20): %.3fms -> %.3fms, %.2fx 개선, 20건 반환
        - 내 물건에 요청한 거래요청 목록 API(/api/trade/get/received, LIMIT 없음): %.3fms -> %.3fms, %.2fx 개선, %,d건 반환
        - 내가 내 물건으로 요청한 거래요청 목록 API(/api/trade/get/sent, LIMIT 20): %.3fms -> %.3fms, %.2fx 개선, 20건 반환
        - 내가 내 물건으로 요청한 거래요청 목록 API(/api/trade/get/sent, LIMIT 없음): %.3fms -> %.3fms, %.2fx 개선, %,d건 반환
        %n""",
        MOCK_ROW_COUNT + 4,
        MOCK_ROW_COUNT + (HOT_ROW_COUNT * 2) + 1,
        MEASURE_REPETITIONS,
        noIndexPlan.targetTableSeqScanUsed(),
        indexedPlan.allTargetIndexesUsed(),
        noIndexResult.activePairLookupAverageMs(),
        indexedResult.activePairLookupAverageMs(),
        noIndexResult.activePairLookupAverageMs() / indexedResult.activePairLookupAverageMs(),
        noIndexResult.receivedListLimitAverageMs(),
        indexedResult.receivedListLimitAverageMs(),
        noIndexResult.receivedListLimitAverageMs() / indexedResult.receivedListLimitAverageMs(),
        noIndexResult.receivedListNoLimitAverageMs(),
        indexedResult.receivedListNoLimitAverageMs(),
        noIndexResult.receivedListNoLimitAverageMs() / indexedResult.receivedListNoLimitAverageMs(),
        expectedActiveHotTradeRequestCount(),
        noIndexResult.sentListLimitAverageMs(),
        indexedResult.sentListLimitAverageMs(),
        noIndexResult.sentListLimitAverageMs() / indexedResult.sentListLimitAverageMs(),
        noIndexResult.sentListNoLimitAverageMs(),
        indexedResult.sentListNoLimitAverageMs(),
        noIndexResult.sentListNoLimitAverageMs() / indexedResult.sentListNoLimitAverageMs(),
        expectedActiveHotTradeRequestCount()
    );
  }

  @FunctionalInterface
  private interface SqlRunnable {
    void run() throws SQLException;
  }

  @FunctionalInterface
  private interface SqlParameterBinder {
    void bind(PreparedStatement preparedStatement) throws SQLException;
  }

  private record PerformanceResult(
      double activePairLookupAverageMs,
      double receivedListLimitAverageMs,
      double sentListLimitAverageMs,
      double receivedListNoLimitAverageMs,
      double sentListNoLimitAverageMs
  ) {
  }

  private record PlanResult(
      String activePairLookup,
      String receivedListLimit,
      String sentListLimit,
      String receivedListNoLimit,
      String sentListNoLimit
  ) {
    private String combined() {
      return activePairLookup + receivedListLimit + sentListLimit + receivedListNoLimit + sentListNoLimit;
    }

    private boolean targetTableSeqScanUsed() {
      return activePairLookup.contains("Seq Scan on trade_request_history")
          && receivedListLimit.contains("Seq Scan on trade_request_history")
          && sentListLimit.contains("Seq Scan on trade_request_history");
    }

    private boolean allTargetIndexesUsed() {
      return activePairLookup.contains("uq_trh_active_item_pair")
          && receivedListLimit.contains("idx_trh_take_active_created_date")
          && sentListLimit.contains("idx_trh_give_active_created_date");
    }
  }
}
