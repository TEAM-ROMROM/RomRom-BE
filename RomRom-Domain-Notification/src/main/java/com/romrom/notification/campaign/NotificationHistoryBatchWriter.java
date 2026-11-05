package com.romrom.notification.campaign;

import com.romrom.common.util.JsonUtil;
import com.romrom.notification.event.NotificationType;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 알림함 기록 일괄 저장 (JDBC batch): 회원 1페이지 > INSERT 1묶음 (회원마다 1건씩 저장하지 않음)
 * 컬럼 원본: NotificationHistory 엔티티 (member 연관 컬럼 member_member_id)
 */
@Component
@RequiredArgsConstructor
public class NotificationHistoryBatchWriter {

  private static final String INSERT_NOTIFICATION_HISTORY_SQL = """
      INSERT INTO notification_history (notification_history_id, member_member_id, notification_type, title, body,
        payload, is_read, published_at, created_date, updated_date)
      VALUES (?, ?, ?, ?, ?, ?, false, ?, ?, ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  /**
   * 기록 1행 값: 회원, 제목, 본문
   */
  public record NotificationHistoryRow(UUID memberId, String title, String body) {
  }

  /**
   * 같은 종류·payload·게시 시각으로 여러 회원 기록 저장 > 저장 행 수
   */
  public int insertHistories(List<NotificationHistoryRow> historyRows, NotificationType notificationType,
      Map<String, String> payload, LocalDateTime publishedAt) {
    if (historyRows.isEmpty()) {
      return 0;
    }
    String payloadJson = JsonUtil.convertToJson(payload);
    Timestamp publishedTimestamp = Timestamp.valueOf(publishedAt);
    Timestamp nowTimestamp = Timestamp.valueOf(LocalDateTime.now());
    int[] insertedRowCounts = jdbcTemplate.batchUpdate(INSERT_NOTIFICATION_HISTORY_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement statement, int rowIndex) throws SQLException {
        NotificationHistoryRow historyRow = historyRows.get(rowIndex);
        statement.setObject(1, UUID.randomUUID());
        statement.setObject(2, historyRow.memberId());
        statement.setString(3, notificationType.name());
        statement.setString(4, historyRow.title());
        statement.setString(5, historyRow.body());
        statement.setString(6, payloadJson);
        statement.setTimestamp(7, publishedTimestamp);
        statement.setTimestamp(8, nowTimestamp);
        statement.setTimestamp(9, nowTimestamp);
      }

      @Override
      public int getBatchSize() {
        return historyRows.size();
      }
    });
    return insertedRowCounts.length;
  }
}
