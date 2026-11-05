package com.romrom.notification.campaign;

import com.romrom.notification.campaign.NotificationHistoryBatchWriter.NotificationHistoryRow;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.push.FcmBatchSendResult;
import com.romrom.notification.push.FcmBatchSender;
import com.romrom.notification.push.FcmPushContent;
import com.romrom.notification.push.FcmPushTarget;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 마케팅 수신 동의 2년 안내 (#874): 대상 500명씩 선점(안내 시각 기록, SKIP LOCKED) > 알림함 기록 > 방해금지 아닌 회원 푸시
 * 법정 안내라 콘텐츠 알림 동의와 무관하게 발송, 다중 인스턴스 동시 실행에도 회원당 1회
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketingConsentReminderService {

  // 1회 선점 회원 수
  static final int REMINDER_BATCH_SIZE = 500;
  // 1회 실행 최대 묶음 수 (하루 최대 10,000명)
  static final int MAXIMUM_BATCHES_PER_RUN = 20;

  // 기준 시각(GREATEST) <= 현재 - 2년 > 안내 시각 기록 후 반환 (MarketingConsentReminderRule 과 같은 기준)
  private static final String CLAIM_DUE_MEMBERS_SQL = """
      UPDATE member SET marketing_consent_reminded_at = :now
      WHERE member_id IN (
        SELECT due_member.member_id FROM member due_member
        WHERE due_member.is_deleted = false
          AND due_member.is_marketing_info_agreed = true
          AND GREATEST(due_member.marketing_consent_changed_at, due_member.marketing_consent_reminded_at) <= :dueBaseBefore
        ORDER BY due_member.member_id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED)
      RETURNING member_id, marketing_consent_changed_at, is_do_not_disturb_enabled,
        do_not_disturb_start_time, do_not_disturb_end_time
      """;

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  private final NotificationHistoryBatchWriter notificationHistoryBatchWriter;
  private final PushCampaignTargetReader pushCampaignTargetReader;
  private final FcmBatchSender fcmBatchSender;

  private record ClaimedReminderMember(UUID memberId, LocalDateTime consentChangedAt,
      PushCampaignTargetMember targetMember) {
  }

  /**
   * 안내 대상 전체 처리 > 안내한 회원 수
   */
  public int sendDueReminders() {
    int remindedMemberCount = 0;
    for (int batchIndex = 0; batchIndex < MAXIMUM_BATCHES_PER_RUN; batchIndex++) {
      LocalDateTime now = LocalDateTime.now();
      List<ClaimedReminderMember> claimedMembers = claimDueMembers(now);
      if (claimedMembers.isEmpty()) {
        break;
      }
      sendReminderBatch(claimedMembers, now);
      remindedMemberCount += claimedMembers.size();
      if (claimedMembers.size() < REMINDER_BATCH_SIZE) {
        break;
      }
    }
    return remindedMemberCount;
  }

  private List<ClaimedReminderMember> claimDueMembers(LocalDateTime now) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("now", Timestamp.valueOf(now));
    parameters.put("dueBaseBefore", Timestamp.valueOf(now.minus(MarketingConsentReminderRule.REMINDER_INTERVAL)));
    parameters.put("batchSize", REMINDER_BATCH_SIZE);
    return namedParameterJdbcTemplate.query(CLAIM_DUE_MEMBERS_SQL, parameters, (resultSet, rowNumber) -> {
      UUID memberId = resultSet.getObject("member_id", UUID.class);
      Timestamp consentChangedTimestamp = resultSet.getTimestamp("marketing_consent_changed_at");
      return new ClaimedReminderMember(memberId,
          consentChangedTimestamp == null ? null : consentChangedTimestamp.toLocalDateTime(),
          new PushCampaignTargetMember(memberId, true, resultSet.getBoolean("is_do_not_disturb_enabled"),
              toLocalTime(resultSet.getTime("do_not_disturb_start_time")),
              toLocalTime(resultSet.getTime("do_not_disturb_end_time"))));
    });
  }

  // 묶음 1개: 알림함 기록 > 방해금지 아닌 회원 토큰 1회 조회 > 회원별 본문 발송
  private void sendReminderBatch(List<ClaimedReminderMember> claimedMembers, LocalDateTime publishedAt) {
    Map<UUID, String> reminderBodyByMemberId = new HashMap<>();
    for (ClaimedReminderMember claimedMember : claimedMembers) {
      reminderBodyByMemberId.put(claimedMember.memberId(),
          MarketingConsentReminderRule.toReminderBody(claimedMember.consentChangedAt()));
    }
    Map<String, String> payload = new HashMap<>();
    payload.put("notificationType", NotificationType.ANNOUNCEMENT.name());
    payload.put("publishedAt", publishedAt.toString());

    List<NotificationHistoryRow> historyRows = claimedMembers.stream()
        .map(claimedMember -> new NotificationHistoryRow(claimedMember.memberId(),
            MarketingConsentReminderRule.REMINDER_TITLE, reminderBodyByMemberId.get(claimedMember.memberId())))
        .toList();
    notificationHistoryBatchWriter.insertHistories(historyRows, NotificationType.ANNOUNCEMENT, payload, publishedAt);

    LocalTime kstNow = AdvertisementPushRule.nowInKorea().toLocalTime();
    List<UUID> pushAllowedMemberIds = claimedMembers.stream()
        .filter(claimedMember -> claimedMember.targetMember().isPushAllowedAt(kstNow))
        .map(ClaimedReminderMember::memberId)
        .toList();
    List<FcmPushTarget> pushTargets = pushCampaignTargetReader.readPushTargets(pushAllowedMemberIds, null);
    FcmBatchSendResult sendResult = fcmBatchSender.send(pushTargets, pushTarget -> new FcmPushContent(
        MarketingConsentReminderRule.REMINDER_TITLE, reminderBodyByMemberId.get(pushTarget.memberId()), null,
        payload, FcmPushContent.DEFAULT_TIME_TO_LIVE, null));
    log.info("[marketing consent reminder] batch sent : members={}, success={}, failure={}",
        claimedMembers.size(), sendResult.successCount(), sendResult.failureCount());
  }

  private static LocalTime toLocalTime(Time sqlTime) {
    return sqlTime == null ? null : sqlTime.toLocalTime();
  }
}
