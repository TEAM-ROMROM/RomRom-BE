package com.romrom.notification.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 캠페인 선점·실행 단위 테스트 (#874): 선점 성공만 실행, 두 인스턴스 동시 실행 시 1회, 실행기 거절 시 반납
 */
class PushCampaignDispatcherTest {

  private PushCampaignRepository pushCampaignRepository;
  private PushCampaignSendService pushCampaignSendService;
  private final List<Runnable> submittedTasks = new ArrayList<>();
  private PushCampaignDispatcher pushCampaignDispatcher;

  @BeforeEach
  void setUp() {
    pushCampaignRepository = mock(PushCampaignRepository.class);
    pushCampaignSendService = mock(PushCampaignSendService.class);
    Executor capturingExecutor = submittedTasks::add;
    pushCampaignDispatcher = new PushCampaignDispatcher(pushCampaignRepository, pushCampaignSendService, capturingExecutor);
  }

  @Test
  @DisplayName("선점 성공(1행) > 실행기에 1건 제출 > 실행 시 발송")
  void claimAndDispatch_선점성공() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.claimScheduledCampaign(eq(campaignId), any())).thenReturn(1);

    Assertions.assertTrue(pushCampaignDispatcher.claimAndDispatch(campaignId));
    Assertions.assertEquals(1, submittedTasks.size());
    submittedTasks.get(0).run();
    verify(pushCampaignSendService).runClaimedCampaign(campaignId);
  }

  @Test
  @DisplayName("선점 실패(0행: 다른 인스턴스 선점·취소) > 실행 안 함")
  void claimAndDispatch_선점실패() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.claimScheduledCampaign(eq(campaignId), any())).thenReturn(0);

    Assertions.assertFalse(pushCampaignDispatcher.claimAndDispatch(campaignId));
    Assertions.assertTrue(submittedTasks.isEmpty());
    verify(pushCampaignSendService, never()).runClaimedCampaign(any());
  }

  @Test
  @DisplayName("두 인스턴스가 같은 예약 캠페인을 동시에 집어도 조건부 UPDATE 1회 성공 > 발송 1회")
  void dispatchDueCampaigns_중복발송없음() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.findDueCampaignIds(eq(PushCampaignStatus.SCHEDULED), any(LocalDateTime.class), any()))
        .thenReturn(List.of(campaignId));
    // DB 조건부 UPDATE: 첫 호출만 1행, 이후 SENDING 이라 0행
    when(pushCampaignRepository.claimScheduledCampaign(eq(campaignId), any())).thenReturn(1).thenReturn(0);
    PushCampaignDispatcher secondInstanceDispatcher =
        new PushCampaignDispatcher(pushCampaignRepository, pushCampaignSendService, submittedTasks::add);

    int firstDispatchedCount = pushCampaignDispatcher.dispatchDueCampaigns();
    int secondDispatchedCount = secondInstanceDispatcher.dispatchDueCampaigns();

    Assertions.assertEquals(1, firstDispatchedCount + secondDispatchedCount);
    Assertions.assertEquals(1, submittedTasks.size());
    verify(pushCampaignRepository, times(2)).claimScheduledCampaign(eq(campaignId), any());
  }

  @Test
  @DisplayName("실행기 거절 > 선점 반납(SCHEDULED) 후 다음 주기 재시도")
  void claimAndDispatch_실행기거절_반납() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.claimScheduledCampaign(eq(campaignId), any())).thenReturn(1);
    PushCampaignDispatcher rejectingDispatcher = new PushCampaignDispatcher(pushCampaignRepository,
        pushCampaignSendService, task -> {
          throw new RejectedExecutionException("busy");
        });

    Assertions.assertFalse(rejectingDispatcher.claimAndDispatch(campaignId));
    verify(pushCampaignRepository).releaseClaimedCampaign(eq(campaignId), any());
  }

  @Test
  @DisplayName("테스트 발송: 선점 성공 시 현재 스레드에서 바로 실행")
  void claimAndRunNow_동기실행() {
    UUID campaignId = UUID.randomUUID();
    when(pushCampaignRepository.claimScheduledCampaign(eq(campaignId), any())).thenReturn(1);

    Assertions.assertTrue(pushCampaignDispatcher.claimAndRunNow(campaignId));
    verify(pushCampaignSendService).runClaimedCampaign(campaignId);
    Assertions.assertTrue(submittedTasks.isEmpty());
  }
}
