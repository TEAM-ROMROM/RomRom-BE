package com.romrom.notification.campaign;

import com.romrom.common.constant.DeviceType;
import com.romrom.notification.push.FcmPushTarget;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 캠페인 대상 조회 (JDBC): 대상·동의 회원 수, 토큰 수, 회원 ID 순 페이지(keyset), 페이지 회원 토큰 1회 조회
 */
@Component
@RequiredArgsConstructor
public class PushCampaignTargetReader {

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  public long countTargetMembers(PushCampaignTargetQuery targetQuery) {
    String countSql = "SELECT COUNT(*) FROM member m WHERE " + targetQuery.targetWhereClause();
    return queryForCount(countSql, targetQuery.parameters());
  }

  public long countPushConsentedMembers(PushCampaignTargetQuery targetQuery) {
    String countSql = "SELECT COUNT(*) FROM member m WHERE " + targetQuery.targetWhereClause()
        + " AND " + targetQuery.pushConsentClause();
    return queryForCount(countSql, targetQuery.parameters());
  }

  /**
   * 수신 동의 회원의 토큰 수 (방해금지는 발송 시점 판정이라 미반영)
   */
  public long countPushConsentedTokens(PushCampaignTargetQuery targetQuery) {
    Map<String, Object> parameters = new HashMap<>(targetQuery.parameters());
    StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM fcm_token f JOIN member m ON m.member_id = f.member_member_id WHERE ")
        .append(targetQuery.targetWhereClause())
        .append(" AND ").append(targetQuery.pushConsentClause());
    if (targetQuery.tokenDeviceType() != null) {
      countSql.append(" AND f.device_type = :tokenDeviceTypeOrdinal");
      parameters.put("tokenDeviceTypeOrdinal", targetQuery.tokenDeviceType().ordinal());
    }
    return queryForCount(countSql.toString(), parameters);
  }

  /**
   * 대상 회원 1페이지: 회원 ID 오름차순, afterMemberId 다음부터 (null > 처음)
   */
  public List<PushCampaignTargetMember> readTargetPage(PushCampaignTargetQuery targetQuery, UUID afterMemberId, int pageSize) {
    Map<String, Object> parameters = new HashMap<>(targetQuery.parameters());
    parameters.put("pageSize", pageSize);
    StringBuilder pageSql = new StringBuilder("SELECT m.member_id, (")
        .append(targetQuery.pushConsentClause()).append(") AS is_push_consented,")
        .append(" m.is_do_not_disturb_enabled, m.do_not_disturb_start_time, m.do_not_disturb_end_time")
        .append(" FROM member m WHERE ").append(targetQuery.targetWhereClause());
    if (afterMemberId != null) {
      pageSql.append(" AND m.member_id > :afterMemberId");
      parameters.put("afterMemberId", afterMemberId);
    }
    pageSql.append(" ORDER BY m.member_id LIMIT :pageSize");
    return namedParameterJdbcTemplate.query(pageSql.toString(), parameters, (resultSet, rowNumber) ->
        new PushCampaignTargetMember(
            resultSet.getObject("member_id", UUID.class),
            resultSet.getBoolean("is_push_consented"),
            resultSet.getBoolean("is_do_not_disturb_enabled"),
            toLocalTime(resultSet.getTime("do_not_disturb_start_time")),
            toLocalTime(resultSet.getTime("do_not_disturb_end_time"))));
  }

  /**
   * 회원 묶음의 토큰 (1회 조회, 기기 종류 지정 시 해당 기기만)
   */
  public List<FcmPushTarget> readPushTargets(Collection<UUID> memberIds, DeviceType tokenDeviceType) {
    if (memberIds.isEmpty()) {
      return List.of();
    }
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("memberIds", memberIds);
    StringBuilder tokenSql = new StringBuilder(
        "SELECT f.token, f.member_member_id, f.device_type FROM fcm_token f"
            + " WHERE f.member_member_id IN (:memberIds) AND f.token IS NOT NULL AND f.token <> ''");
    if (tokenDeviceType != null) {
      tokenSql.append(" AND f.device_type = :tokenDeviceTypeOrdinal");
      parameters.put("tokenDeviceTypeOrdinal", tokenDeviceType.ordinal());
    }
    return namedParameterJdbcTemplate.query(tokenSql.toString(), parameters, (resultSet, rowNumber) ->
        new FcmPushTarget(
            resultSet.getString("token"),
            resultSet.getObject("member_member_id", UUID.class),
            toDeviceType(resultSet)));
  }

  private long queryForCount(String countSql, Map<String, Object> parameters) {
    Long rowCount = namedParameterJdbcTemplate.queryForObject(countSql, parameters, Long.class);
    return rowCount == null ? 0L : rowCount;
  }

  private static LocalTime toLocalTime(Time sqlTime) {
    return sqlTime == null ? null : sqlTime.toLocalTime();
  }

  // device_type smallint 순번 > 기기 종류 (범위 밖·null > null)
  private static DeviceType toDeviceType(ResultSet resultSet) throws SQLException {
    int deviceTypeOrdinal = resultSet.getInt("device_type");
    if (resultSet.wasNull() || deviceTypeOrdinal < 0 || deviceTypeOrdinal >= DeviceType.values().length) {
      return null;
    }
    return DeviceType.values()[deviceTypeOrdinal];
  }
}
