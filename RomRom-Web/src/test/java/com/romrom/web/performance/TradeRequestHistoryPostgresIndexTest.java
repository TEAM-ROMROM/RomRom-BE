package com.romrom.web.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
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
  void actualTradeRequestTableBlocksConcurrentDuplicateActiveRequests() throws Exception {
    UUID memberA = UUID.randomUUID();
    UUID memberB = UUID.randomUUID();
    UUID itemA = UUID.randomUUID();
    UUID itemB = UUID.randomUUID();
    String runId = UUID.randomUUID().toString();
    AtomicInteger duplicateKeyCount = new AtomicInteger();

    try (Connection connection = connect()) {
      // 실제 trade_request_history 테이블의 운영 인덱스로 중복 요청 차단을 검증한다.
      createActualMigrationIndexes(connection);
      insertActualMember(connection, memberA, "rr-concurrency-a-" + runId);
      insertActualMember(connection, memberB, "rr-concurrency-b-" + runId);
      insertActualItem(connection, itemA, memberA, "rr-concurrency-take-" + runId);
      insertActualItem(connection, itemB, memberB, "rr-concurrency-give-" + runId);
    }

    ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
    CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Future<Boolean>> futures = new java.util.ArrayList<>();

    for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
      // A->B와 B->A를 섞어 실제 거래요청 row를 insert한다.
      boolean reversePair = i % 2 == 0;
      futures.add(executorService.submit(insertActualTradeRequestTask(
          itemA, itemB, reversePair, readyLatch, startLatch, duplicateKeyCount
      )));
    }

    assertThat(readyLatch.await(10, TimeUnit.SECONDS)).isTrue();
    startLatch.countDown();

    int successCount = 0;
    for (Future<Boolean> future : futures) {
      if (future.get(10, TimeUnit.SECONDS)) {
        successCount++;
      }
    }
    executorService.shutdown();
    assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    long activeRowCount;
    long canceledRowCount;
    try (Connection connection = connect()) {
      activeRowCount = countActualTradeRequestsForPair(connection, itemA, itemB, "trade_status IN (0, 1, 3, 4)");
      assertThat(activeRowCount).isEqualTo(1L);

      // CANCELED는 partial unique index 대상이 아니므로 같은 물품쌍 재요청을 막지 않는다.
      insertActualTradeRequest(connection, itemA, itemB, 2);
      insertActualTradeRequest(connection, itemB, itemA, 2);
      canceledRowCount = countActualTradeRequestsForPair(connection, itemA, itemB, "trade_status = 2");
      assertThat(canceledRowCount).isEqualTo(2L);
    } finally {
      cleanupActualRows(List.of(itemA, itemB), List.of(memberA, memberB));
    }

    printConcurrencyLog(successCount, duplicateKeyCount.get(), activeRowCount, canceledRowCount);

    assertThat(successCount).isEqualTo(1);
    assertThat(duplicateKeyCount.get()).isEqualTo(CONCURRENT_REQUESTS - 1);
  }

  @Test
  void compareLookupPerformanceOnActualItemAndTradeRequestTables() throws Exception {
    validateMockCounts();

    UUID memberA = UUID.randomUUID();
    UUID memberB = UUID.randomUUID();
    UUID targetTakeItemId = UUID.randomUUID();
    UUID targetGiveItemId = UUID.randomUUID();
    UUID hotTakeItemId = UUID.randomUUID();
    String runId = UUID.randomUUID().toString();
    String itemIdPrefix = "rr-perf-item-" + runId + "-";
    String tradeIdPrefix = "rr-perf-trade-" + runId + "-";
    String hotTradeIdPrefix = "rr-perf-hot-trade-" + runId + "-";

    PerformanceResult noIndexResult;
    PerformanceResult indexedResult;

    try (Connection connection = connect()) {
      connection.setAutoCommit(false);
      try {
        // 실제 member/item/trade_request_history 테이블에 대량 목데이터를 넣고, 테스트 후 rollback한다.
        dropActualMigrationIndexes(connection);
        seedActualPerformanceRows(
            connection,
            memberA,
            memberB,
            targetTakeItemId,
            targetGiveItemId,
            hotTakeItemId,
            itemIdPrefix,
            tradeIdPrefix,
            hotTradeIdPrefix,
            runId
        );

        noIndexResult = measurePerformance(connection, targetTakeItemId, targetGiveItemId, hotTakeItemId);

        createActualMigrationIndexes(connection);
        analyzeActualTables(connection);

        indexedResult = measurePerformance(connection, targetTakeItemId, targetGiveItemId, hotTakeItemId);
        connection.rollback();
      } catch (Exception e) {
        connection.rollback();
        throw e;
      }
    }

    printPerformanceLog(noIndexResult, indexedResult);
    writePerformanceReport(noIndexResult, indexedResult);

    assertThat(indexedResult.activePairLookupAverageMs()).isLessThan(noIndexResult.activePairLookupAverageMs());
    assertThat(indexedResult.receivedListAverageMs()).isLessThan(noIndexResult.receivedListAverageMs());
  }

  private Callable<Boolean> insertActualTradeRequestTask(
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
        insertActualTradeRequest(connection, takeItemId, giveItemId, 0);
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
      UUID targetTakeItemId,
      UUID targetGiveItemId,
      UUID hotTakeItemId
  ) throws SQLException {
    double activePairLookupAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeActualActivePairLookup(connection, targetTakeItemId, targetGiveItemId)
    );
    double receivedListAverageMs = averageMs(
        MEASURE_REPETITIONS,
        () -> executeActualReceivedListLookup(connection, hotTakeItemId)
    );
    return new PerformanceResult(activePairLookupAverageMs, receivedListAverageMs);
  }

  private void executeActualActivePairLookup(Connection connection, UUID takeItemId, UUID giveItemId) throws SQLException {
    // 실제 Repository의 existsTradeRequestBetweenItems native query와 같은 조건이다.
    String sql = """
        SELECT EXISTS (
            SELECT 1
            FROM trade_request_history t
            WHERE t.trade_status IN (0, 1, 3, 4)
              AND LEAST(t.take_item_item_id, t.give_item_item_id) = LEAST(CAST(? AS uuid), CAST(? AS uuid))
              AND GREATEST(t.take_item_item_id, t.give_item_item_id) = GREATEST(CAST(? AS uuid), CAST(? AS uuid))
        )
        """;
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setObject(1, takeItemId);
      preparedStatement.setObject(2, giveItemId);
      preparedStatement.setObject(3, takeItemId);
      preparedStatement.setObject(4, giveItemId);
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getBoolean(1)).isTrue();
      }
    }
  }

  private void executeActualReceivedListLookup(Connection connection, UUID hotTakeItemId) throws SQLException {
    // 실제 item row와 join해서 AVAILABLE 물품의 받은 거래요청 최신순 조회 패턴을 검증한다.
    String sql = """
        SELECT t.trade_request_history_id
        FROM trade_request_history t
        JOIN item i ON i.item_id = t.take_item_item_id
        WHERE t.take_item_item_id = ?
          AND i.item_status = 'AVAILABLE'
          AND t.trade_status IN (0, 1, 3, 4)
        ORDER BY t.created_date DESC
        LIMIT 20
        """;
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      preparedStatement.setObject(1, hotTakeItemId);
      int rowCount = 0;
      try (ResultSet resultSet = preparedStatement.executeQuery()) {
        while (resultSet.next()) {
          rowCount++;
        }
      }
      assertThat(rowCount).isEqualTo(20);
    }
  }

  private double averageMs(int repetitions, SqlRunnable runnable) throws SQLException {
    // 쿼리 플랜/캐시 워밍업을 간단히 거친 뒤 평균 ms를 계산한다.
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

  private void seedActualPerformanceRows(
      Connection connection,
      UUID memberA,
      UUID memberB,
      UUID targetTakeItemId,
      UUID targetGiveItemId,
      UUID hotTakeItemId,
      String itemIdPrefix,
      String tradeIdPrefix,
      String hotTradeIdPrefix,
      String runId
  ) throws SQLException {
    insertActualMember(connection, memberA, "rr-perf-a-" + runId);
    insertActualMember(connection, memberB, "rr-perf-b-" + runId);
    insertActualItem(connection, targetTakeItemId, memberA, "rr-perf-target-take-" + runId);
    insertActualItem(connection, targetGiveItemId, memberB, "rr-perf-target-give-" + runId);
    insertActualItem(connection, hotTakeItemId, memberA, "rr-perf-hot-take-" + runId);
    insertPerformanceItems(connection, memberA, memberB, itemIdPrefix);
    insertBaseTradeRequests(connection, itemIdPrefix, tradeIdPrefix);
    insertHotTradeRequests(connection, hotTakeItemId, itemIdPrefix, hotTradeIdPrefix);
    insertActualTradeRequest(connection, targetTakeItemId, targetGiveItemId, 0);
    analyzeActualTables(connection);
  }

  private void insertPerformanceItems(Connection connection, UUID memberA, UUID memberB, String itemIdPrefix) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO item (
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
            CASE WHEN g % 2 = 0 THEN CAST(? AS uuid) ELSE CAST(? AS uuid) END,
            'rr-perf-item-' || g::text,
            'trade request index performance mock item',
            ((g % 25) + 1)::integer,
            'SLIGHTLY_USED',
            'AVAILABLE',
            0,
            1000,
            false,
            false,
            now(),
            now()
        FROM generate_series(1, ?) AS g
        """)) {
      preparedStatement.setString(1, itemIdPrefix);
      preparedStatement.setObject(2, memberA);
      preparedStatement.setObject(3, memberB);
      preparedStatement.setInt(4, MOCK_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertBaseTradeRequests(Connection connection, String itemIdPrefix, String tradeIdPrefix) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO trade_request_history (
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
            CASE g % 5
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
        """)) {
      preparedStatement.setString(1, tradeIdPrefix);
      preparedStatement.setString(2, itemIdPrefix);
      preparedStatement.setString(3, itemIdPrefix);
      preparedStatement.setInt(4, MOCK_ROW_COUNT);
      preparedStatement.setInt(5, MOCK_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertHotTradeRequests(
      Connection connection,
      UUID hotTakeItemId,
      String itemIdPrefix,
      String hotTradeIdPrefix
  ) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO trade_request_history (
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
            CASE WHEN g % 7 = 0 THEN 2 ELSE 0 END,
            true,
            now() - (g * interval '1 second'),
            now() - (g * interval '1 second')
        FROM generate_series(1, ?) AS g
        """)) {
      preparedStatement.setString(1, hotTradeIdPrefix);
      preparedStatement.setObject(2, hotTakeItemId);
      preparedStatement.setString(3, itemIdPrefix);
      preparedStatement.setInt(4, HOT_ROW_COUNT);
      preparedStatement.executeUpdate();
    }
  }

  private void insertActualMember(Connection connection, UUID memberId, String marker) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO member (
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
        """)) {
      preparedStatement.setObject(1, memberId);
      preparedStatement.setString(2, marker + "@romrom.test");
      preparedStatement.setString(3, marker);
      preparedStatement.executeUpdate();
    }
  }

  private void insertActualItem(Connection connection, UUID itemId, UUID memberId, String itemName) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO item (
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
        """)) {
      preparedStatement.setObject(1, itemId);
      preparedStatement.setObject(2, memberId);
      preparedStatement.setString(3, itemName);
      preparedStatement.executeUpdate();
    }
  }

  private void insertActualTradeRequest(Connection connection, UUID takeItemId, UUID giveItemId, int tradeStatus) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement("""
        INSERT INTO trade_request_history (
            trade_request_history_id,
            take_item_item_id,
            give_item_item_id,
            trade_status,
            is_new,
            created_date,
            updated_date
        )
        VALUES (?, ?, ?, ?, true, ?, ?)
        """)) {
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

  private long countActualTradeRequestsForPair(
      Connection connection,
      UUID itemA,
      UUID itemB,
      String statusWhereClause
  ) throws SQLException {
    String sql = """
        SELECT COUNT(*)
        FROM trade_request_history
        WHERE %s
          AND LEAST(take_item_item_id, give_item_item_id) = LEAST(CAST(? AS uuid), CAST(? AS uuid))
          AND GREATEST(take_item_item_id, give_item_item_id) = GREATEST(CAST(? AS uuid), CAST(? AS uuid))
        """.formatted(statusWhereClause);
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

  private void createActualMigrationIndexes(Connection connection) throws SQLException {
    execute(connection, """
        CREATE UNIQUE INDEX IF NOT EXISTS uq_trh_active_item_pair
            ON trade_request_history (
                LEAST(take_item_item_id, give_item_item_id),
                GREATEST(take_item_item_id, give_item_item_id)
            )
            WHERE trade_status IN (0, 1, 3, 4)
        """);
    execute(connection, """
        CREATE INDEX IF NOT EXISTS idx_trh_take_active_created_date
            ON trade_request_history (take_item_item_id, created_date DESC)
            WHERE trade_status IN (0, 1, 3, 4)
        """);
    execute(connection, """
        CREATE INDEX IF NOT EXISTS idx_trh_give_active_created_date
            ON trade_request_history (give_item_item_id, created_date DESC)
            WHERE trade_status IN (0, 1, 3, 4)
        """);
  }

  private void dropActualMigrationIndexes(Connection connection) throws SQLException {
    execute(connection, "DROP INDEX IF EXISTS uq_trh_active_item_pair");
    execute(connection, "DROP INDEX IF EXISTS idx_trh_take_active_created_date");
    execute(connection, "DROP INDEX IF EXISTS idx_trh_give_active_created_date");
  }

  private void analyzeActualTables(Connection connection) throws SQLException {
    execute(connection, "ANALYZE member");
    execute(connection, "ANALYZE item");
    execute(connection, "ANALYZE trade_request_history");
  }

  private void cleanupActualRows(List<UUID> itemIds, List<UUID> memberIds) throws SQLException {
    try (Connection connection = connect()) {
      Array itemIdArray = connection.createArrayOf("uuid", itemIds.toArray());
      Array memberIdArray = connection.createArrayOf("uuid", memberIds.toArray());

      executeWithArray(connection, """
          DELETE FROM trade_request_history_item_trade_options
          WHERE trade_request_history_trade_request_history_id IN (
              SELECT trade_request_history_id
              FROM trade_request_history
              WHERE take_item_item_id = ANY (?)
                 OR give_item_item_id = ANY (?)
          )
          """, itemIdArray, itemIdArray);
      executeWithArray(connection, """
          DELETE FROM trade_request_history
          WHERE take_item_item_id = ANY (?)
             OR give_item_item_id = ANY (?)
          """, itemIdArray, itemIdArray);
      executeWithArray(connection, "DELETE FROM item_item_trade_options WHERE item_item_id = ANY (?)", itemIdArray);
      executeWithArray(connection, "DELETE FROM item WHERE item_id = ANY (?)", itemIdArray);
      executeWithArray(connection, "DELETE FROM member WHERE member_id = ANY (?)", memberIdArray);

      itemIdArray.free();
      memberIdArray.free();
    }
  }

  private void executeWithArray(Connection connection, String sql, Array... arrays) throws SQLException {
    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
      for (int i = 0; i < arrays.length; i++) {
        preparedStatement.setArray(i + 1, arrays[i]);
      }
      preparedStatement.executeUpdate();
    }
  }

  private void execute(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private Connection connect() throws SQLException {
    // 테스트 서버에서 재현할 수 있도록 환경변수/시스템 프로퍼티를 우선하고, 로컬 기본값을 마지막에 사용한다.
    return DriverManager.getConnection(
        propertyOrEnv("romrom.test.postgres.url", "ROMROM_TEST_POSTGRES_URL", "jdbc:postgresql://localhost:5432/romrom"),
        propertyOrEnv("romrom.test.postgres.username", "ROMROM_TEST_POSTGRES_USERNAME", "postgres"),
        propertyOrEnv("romrom.test.postgres.password", "ROMROM_TEST_POSTGRES_PASSWORD", "postgres")
    );
  }

  private String propertyOrEnv(String propertyName, String envName, String defaultValue) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    return defaultValue;
  }

  private void validateMockCounts() {
    assertThat(MOCK_ROW_COUNT).isGreaterThanOrEqualTo(3);
    assertThat(HOT_ROW_COUNT).isGreaterThanOrEqualTo(24);
    assertThat(HOT_ROW_COUNT).isLessThanOrEqualTo(MOCK_ROW_COUNT);
  }

  private void printConcurrencyLog(
      int successCount,
      int duplicateKeyCount,
      long activeRowCount,
      long canceledRowCount
  ) {
    System.out.printf("""
        [TradeRequestHistoryPostgresIndexTest] 실제 테이블 동시성 테스트 결과
        - 실제 member 목데이터: 2건
        - 실제 item 목데이터: 2건
        - 동시 거래요청 insert 수: %,d
        - insert 성공 수: %,d
        - unique 충돌 수: %,d
        - 활성 거래요청 row 수: %,d
        - 취소 거래요청 row 수: %,d
        - 결론: 같은 물품쌍 활성 거래요청은 실제 trade_request_history 테이블에서 1건만 허용됨
        %n""",
        CONCURRENT_REQUESTS,
        successCount,
        duplicateKeyCount,
        activeRowCount,
        canceledRowCount
    );
  }

  private void printPerformanceLog(PerformanceResult noIndexResult, PerformanceResult indexedResult) {
    System.out.printf("""
        [TradeRequestHistoryPostgresIndexTest] 실제 item/trade_request_history 조회 성능 테스트 결과
        - 실제 member 목데이터: 2건
        - 실제 item 목데이터: %,d건
        - 실제 trade_request_history 목데이터: %,d건
        - 반복 측정 횟수: %,d
        - 활성 물품쌍 중복 조회: %.3fms -> %.3fms, %.2fx 개선
        - 받은 거래요청 목록 조회: %.3fms -> %.3fms, %.2fx 개선
        %n""",
        MOCK_ROW_COUNT + 3,
        MOCK_ROW_COUNT + HOT_ROW_COUNT + 1,
        MEASURE_REPETITIONS,
        noIndexResult.activePairLookupAverageMs(),
        indexedResult.activePairLookupAverageMs(),
        noIndexResult.activePairLookupAverageMs() / indexedResult.activePairLookupAverageMs(),
        noIndexResult.receivedListAverageMs(),
        indexedResult.receivedListAverageMs(),
        noIndexResult.receivedListAverageMs() / indexedResult.receivedListAverageMs()
    );
  }

  private void writePerformanceReport(PerformanceResult noIndexResult, PerformanceResult indexedResult) throws IOException {
    Path reportPath = Path.of("build", "reports", "trade-request-history-index-performance.md");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, """
        # TradeRequestHistory Postgres Index Performance

        - actual member rows: 2
        - actual item rows: %,d
        - actual trade_request_history rows: %,d
        - repetitions: %,d

        | Query | Without Index (ms) | With Index (ms) | Improvement |
        |---|---:|---:|---:|
        | Active item-pair duplicate lookup | %.3f | %.3f | %.2fx |
        | Received trade request list | %.3f | %.3f | %.2fx |
        """.formatted(
        MOCK_ROW_COUNT + 3,
        MOCK_ROW_COUNT + HOT_ROW_COUNT + 1,
        MEASURE_REPETITIONS,
        noIndexResult.activePairLookupAverageMs(),
        indexedResult.activePairLookupAverageMs(),
        noIndexResult.activePairLookupAverageMs() / indexedResult.activePairLookupAverageMs(),
        noIndexResult.receivedListAverageMs(),
        indexedResult.receivedListAverageMs(),
        noIndexResult.receivedListAverageMs() / indexedResult.receivedListAverageMs()
    ));
  }

  @FunctionalInterface
  private interface SqlRunnable {
    void run() throws SQLException;
  }

  private record PerformanceResult(double activePairLookupAverageMs, double receivedListAverageMs) {
  }
}
