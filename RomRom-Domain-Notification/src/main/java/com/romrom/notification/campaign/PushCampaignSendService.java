package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.notification.campaign.NotificationHistoryBatchWriter.NotificationHistoryRow;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.push.FcmBatchSendResult;
import com.romrom.notification.push.FcmBatchSender;
import com.romrom.notification.push.FcmPushContent;
import com.romrom.notification.push.FcmPushTarget;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 선점된(SENDING) 캠페인 실행: 광고 야간 확인 > 회원 1000명 페이지마다 알림함 기록·토큰 1회 조회·500건 묶음 발송·집계 누적 > 완료·실패
 * 대상 회원 전원 기록, 푸시는 수신 동의 + 방해금지 아닌 회원만 (TEST: 관리자 본인, 기록 없음·동의·방해금지 무시)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushCampaignSendService {

  // 대상 회원 페이지 크기 (메모리에 한 번에 올리는 회원 수 상한)
  public static final int TARGET_PAGE_SIZE = 1000;
  static final String NIGHT_RESTRICTED_REASON = "광고성 야간 발송 제한 (21:00~08:00), 발송하지 않음";
  static final String NIGHT_INTERRUPTED_REASON = "광고성 야간 발송 제한 (21:00~08:00) 진입으로 발송 중단";

  private final PushCampaignRepository pushCampaignRepository;
  private final PushCampaignTargetReader pushCampaignTargetReader;
  private final NotificationHistoryBatchWriter notificationHistoryBatchWriter;
  private final FcmBatchSender fcmBatchSender;

  public void runClaimedCampaign(UUID pushCampaignId) {
    PushCampaign pushCampaign = pushCampaignRepository.findById(pushCampaignId).orElse(null);
    if (pushCampaign == null) {
      log.warn("[push campaign] skipped : campaign not found, pushCampaignId={}", pushCampaignId);
      return;
    }
    log.info("[push campaign] started : pushCampaignId={}, purpose={}, targetType={}",
        pushCampaignId, pushCampaign.getPurpose(), pushCampaign.getTargetType());
    try {
      String failureReason = sendAllPages(pushCampaign);
      finishCampaign(pushCampaignId, failureReason);
    } catch (RuntimeException campaignException) {
      log.error("[push campaign] failed : pushCampaignId={}, error={}", pushCampaignId,
          campaignException.getMessage(), campaignException);
      finishCampaign(pushCampaignId, "발송 중 오류: " + campaignException.getMessage());
    }
  }

  // 전체 페이지 발송 > 실패 사유 (정상 완료 > null)
  private String sendAllPages(PushCampaign pushCampaign) {
    PushCampaignPurpose purpose = pushCampaign.getPurpose();
    boolean isTestTarget = pushCampaign.getTargetType() == PushCampaignTargetType.TEST;
    if (isNightRestricted(purpose, isTestTarget)) {
      log.warn("[push campaign] rejected : advertisement night restriction, pushCampaignId={}",
          pushCampaign.getPushCampaignId());
      return NIGHT_RESTRICTED_REASON;
    }

    // 발송 내용 (광고 표시 문구 부착, 보존 시간: 광고 6시간·그 외 1일)
    LocalDateTime publishedAt = LocalDateTime.now();
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(purpose, pushCampaign.getTargetType(),
        PushCampaignTargetFilter.fromJson(pushCampaign.getTargetFilter()), publishedAt);
    Map<String, String> payload = PushCampaignPayloadFactory.createPayload(pushCampaign, publishedAt);
    FcmPushContent pushContent = new FcmPushContent(
        AdvertisementPushRule.toDisplayTitle(purpose, pushCampaign.getTitle()),
        AdvertisementPushRule.toDisplayBody(purpose, pushCampaign.getBody()),
        pushCampaign.getImageUrl(),
        payload,
        purpose == PushCampaignPurpose.ADVERTISEMENT
            ? FcmPushContent.ADVERTISEMENT_TIME_TO_LIVE : FcmPushContent.DEFAULT_TIME_TO_LIVE,
        null);

    // 회원 ID 순 keyset 페이지 반복
    UUID afterMemberId = null;
    while (true) {
      if (afterMemberId != null && isNightRestricted(purpose, isTestTarget)) {
        return NIGHT_INTERRUPTED_REASON;
      }
      List<PushCampaignTargetMember> targetMembers =
          pushCampaignTargetReader.readTargetPage(targetQuery, afterMemberId, TARGET_PAGE_SIZE);
      if (targetMembers.isEmpty()) {
        return null;
      }
      sendPage(pushCampaign, targetMembers, targetQuery, pushContent, isTestTarget, publishedAt);
      if (targetMembers.size() < TARGET_PAGE_SIZE) {
        return null;
      }
      afterMemberId = targetMembers.get(targetMembers.size() - 1).memberId();
    }
  }

  // 페이지 1개: 알림함 기록 일괄 저장 > 푸시 가능 회원 토큰 1회 조회 > 묶음 발송 > 집계 원자적 누적
  private void sendPage(PushCampaign pushCampaign, List<PushCampaignTargetMember> targetMembers,
      PushCampaignTargetQuery targetQuery, FcmPushContent pushContent, boolean isTestTarget, LocalDateTime publishedAt) {
    if (!isTestTarget) {
      NotificationType notificationType = PushCampaignPayloadFactory.toNotificationType(pushCampaign.getPurpose());
      List<NotificationHistoryRow> historyRows = targetMembers.stream()
          .map(targetMember -> new NotificationHistoryRow(targetMember.memberId(), pushContent.title(), pushContent.body()))
          .toList();
      notificationHistoryBatchWriter.insertHistories(historyRows, notificationType, pushContent.data(), publishedAt);
    }

    LocalTime kstNow = AdvertisementPushRule.nowInKorea().toLocalTime();
    List<UUID> pushAllowedMemberIds = targetMembers.stream()
        .filter(targetMember -> isTestTarget || targetMember.isPushAllowedAt(kstNow))
        .map(PushCampaignTargetMember::memberId)
        .toList();
    List<FcmPushTarget> pushTargets =
        pushCampaignTargetReader.readPushTargets(pushAllowedMemberIds, targetQuery.tokenDeviceType());
    FcmBatchSendResult sendResult = fcmBatchSender.send(pushTargets, pushContent);

    pushCampaignRepository.addSendCounts(pushCampaign.getPushCampaignId(), targetMembers.size(),
        sendResult.successCount(), sendResult.failureCount(), sendResult.removedTokenCount());
  }

  private boolean isNightRestricted(PushCampaignPurpose purpose, boolean isTestTarget) {
    return !isTestTarget && AdvertisementPushRule.isRestrictedAdvertisementAt(purpose, AdvertisementPushRule.nowInKorea());
  }

  // 종료 기록: 사유 없음 > COMPLETED, 있음 > FAILED (사유 길이 제한)
  private void finishCampaign(UUID pushCampaignId, String failureReason) {
    PushCampaignStatus finalStatus = failureReason == null ? PushCampaignStatus.COMPLETED : PushCampaignStatus.FAILED;
    String storedFailureReason = failureReason == null ? null
        : failureReason.substring(0, Math.min(failureReason.length(), PushCampaign.FAILURE_REASON_MAXIMUM_LENGTH));
    pushCampaignRepository.finishSendingCampaign(pushCampaignId, finalStatus, storedFailureReason, LocalDateTime.now());
    log.info("[push campaign] finished : pushCampaignId={}, status={}", pushCampaignId, finalStatus);
  }
}
