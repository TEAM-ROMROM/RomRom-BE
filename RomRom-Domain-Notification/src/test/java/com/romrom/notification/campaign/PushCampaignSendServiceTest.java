package com.romrom.notification.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.notification.campaign.NotificationHistoryBatchWriter.NotificationHistoryRow;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.event.NotificationType;
import com.romrom.notification.push.FcmBatchSendResult;
import com.romrom.notification.push.FcmBatchSender;
import com.romrom.notification.push.FcmPushContent;
import com.romrom.notification.repository.PushCampaignRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 캠페인 실행 단위 테스트 (#874): 대상 전원 알림함 기록, 수신 동의 회원만 토큰 조회·발송, 집계 누적, 완료 처리
 */
class PushCampaignSendServiceTest {

  private PushCampaignRepository pushCampaignRepository;
  private PushCampaignTargetReader pushCampaignTargetReader;
  private NotificationHistoryBatchWriter notificationHistoryBatchWriter;
  private FcmBatchSender fcmBatchSender;
  private PushCampaignSendService pushCampaignSendService;

  @BeforeEach
  void setUp() {
    pushCampaignRepository = mock(PushCampaignRepository.class);
    pushCampaignTargetReader = mock(PushCampaignTargetReader.class);
    notificationHistoryBatchWriter = mock(NotificationHistoryBatchWriter.class);
    fcmBatchSender = mock(FcmBatchSender.class);
    pushCampaignSendService = new PushCampaignSendService(pushCampaignRepository, pushCampaignTargetReader,
        notificationHistoryBatchWriter, fcmBatchSender);
  }

  @Test
  @DisplayName("정보성 ALL: 2명 모두 기록, 동의 1명만 토큰 조회·발송, 집계 누적 후 COMPLETED")
  @SuppressWarnings("unchecked")
  void runClaimedCampaign_기록전원_푸시동의자() {
    UUID campaignId = UUID.randomUUID();
    PushCampaign pushCampaign = PushCampaign.builder().pushCampaignId(campaignId).title("공지").body("새 소식")
        .purpose(PushCampaignPurpose.INFORMATION).targetType(PushCampaignTargetType.ALL)
        .targetFilter(PushCampaignTargetFilter.empty().toJson()).status(PushCampaignStatus.SENDING).build();
    UUID consentedMemberId = UUID.randomUUID();
    UUID notConsentedMemberId = UUID.randomUUID();
    when(pushCampaignRepository.findById(campaignId)).thenReturn(Optional.of(pushCampaign));
    when(pushCampaignTargetReader.readTargetPage(any(), isNull(), anyInt())).thenReturn(List.of(
        new PushCampaignTargetMember(consentedMemberId, true, false, null, null),
        new PushCampaignTargetMember(notConsentedMemberId, false, false, null, null)));
    when(pushCampaignTargetReader.readPushTargets(any(), any())).thenReturn(List.of());
    when(fcmBatchSender.send(any(List.class), any(FcmPushContent.class))).thenReturn(new FcmBatchSendResult(1, 0, 0));

    pushCampaignSendService.runClaimedCampaign(campaignId);

    ArgumentCaptor<List<NotificationHistoryRow>> historyRowsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(notificationHistoryBatchWriter).insertHistories(historyRowsCaptor.capture(), eq(NotificationType.ANNOUNCEMENT),
        payloadCaptor.capture(), any());
    Assertions.assertEquals(2, historyRowsCaptor.getValue().size());
    Assertions.assertEquals(campaignId.toString(), payloadCaptor.getValue().get("campaignId"));

    ArgumentCaptor<List<UUID>> pushMemberIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(pushCampaignTargetReader).readPushTargets(pushMemberIdsCaptor.capture(), isNull());
    Assertions.assertEquals(List.of(consentedMemberId), pushMemberIdsCaptor.getValue());

    verify(pushCampaignRepository).addSendCounts(campaignId, 2, 1, 0, 0);
    verify(pushCampaignRepository).finishSendingCampaign(eq(campaignId), eq(PushCampaignStatus.COMPLETED), isNull(), any());
  }

  @Test
  @DisplayName("TEST 대상: 알림함 기록 없음, 동의 여부 무관 발송")
  @SuppressWarnings("unchecked")
  void runClaimedCampaign_테스트_기록없음() {
    UUID campaignId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    PushCampaign testCampaign = PushCampaign.builder().pushCampaignId(campaignId).title("t").body("b")
        .purpose(PushCampaignPurpose.ADVERTISEMENT).targetType(PushCampaignTargetType.TEST)
        .targetFilter(PushCampaignTargetFilter.builder().testMemberId(adminId).build().toJson())
        .status(PushCampaignStatus.SENDING).build();
    when(pushCampaignRepository.findById(campaignId)).thenReturn(Optional.of(testCampaign));
    when(pushCampaignTargetReader.readTargetPage(any(), isNull(), anyInt()))
        .thenReturn(List.of(new PushCampaignTargetMember(adminId, false, true, null, null)));
    when(pushCampaignTargetReader.readPushTargets(any(), any())).thenReturn(List.of());
    when(fcmBatchSender.send(any(List.class), any(FcmPushContent.class))).thenReturn(FcmBatchSendResult.empty());

    pushCampaignSendService.runClaimedCampaign(campaignId);

    verify(notificationHistoryBatchWriter, never()).insertHistories(any(), any(), any(), any());
    ArgumentCaptor<List<UUID>> pushMemberIdsCaptor = ArgumentCaptor.forClass(List.class);
    verify(pushCampaignTargetReader).readPushTargets(pushMemberIdsCaptor.capture(), isNull());
    Assertions.assertEquals(List.of(adminId), pushMemberIdsCaptor.getValue());
    ArgumentCaptor<FcmPushContent> pushContentCaptor = ArgumentCaptor.forClass(FcmPushContent.class);
    verify(fcmBatchSender).send(any(List.class), pushContentCaptor.capture());
    Assertions.assertEquals("(광고) t", pushContentCaptor.getValue().title());
    Assertions.assertEquals(FcmPushContent.ADVERTISEMENT_TIME_TO_LIVE, pushContentCaptor.getValue().timeToLive());
  }

  @Test
  @DisplayName("실행 중 예외 > FAILED + 사유")
  void runClaimedCampaign_예외_FAILED() {
    UUID campaignId = UUID.randomUUID();
    PushCampaign pushCampaign = PushCampaign.builder().pushCampaignId(campaignId).title("t").body("b")
        .purpose(PushCampaignPurpose.INFORMATION).targetType(PushCampaignTargetType.ALL)
        .status(PushCampaignStatus.SENDING).build();
    when(pushCampaignRepository.findById(campaignId)).thenReturn(Optional.of(pushCampaign));
    when(pushCampaignTargetReader.readTargetPage(any(), any(), anyInt())).thenThrow(new IllegalStateException("db down"));

    pushCampaignSendService.runClaimedCampaign(campaignId);

    verify(pushCampaignRepository).finishSendingCampaign(eq(campaignId), eq(PushCampaignStatus.FAILED),
        eq("발송 중 오류: db down"), any());
  }
}
