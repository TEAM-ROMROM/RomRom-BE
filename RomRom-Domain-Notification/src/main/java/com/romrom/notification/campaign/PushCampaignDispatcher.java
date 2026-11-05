package com.romrom.notification.campaign;

import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.notification.config.PushCampaignExecutorConfig;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 캠페인 실행 진입점: 조건부 선점(SCHEDULED > SENDING, 1행 갱신일 때만 실행) > 전용 Executor 비동기 실행
 * 블루그린·다중 인스턴스가 같은 캠페인을 동시에 집어도 선점 성공 1곳만 발송
 */
@Component
@Slf4j
public class PushCampaignDispatcher {

  // 1회 주기 최대 실행 캠페인 수
  static final int MAXIMUM_DUE_CAMPAIGNS_PER_RUN = 20;
  // 시작 후 이 시간 넘게 SENDING > 중단으로 보고 FAILED (재발송 없음)
  static final Duration STALE_SENDING_DURATION = Duration.ofHours(3);
  static final String STALE_SENDING_REASON = "발송 중 서버 중단으로 완료되지 않음 (재발송하지 않음)";

  private final PushCampaignRepository pushCampaignRepository;
  private final PushCampaignSendService pushCampaignSendService;
  private final Executor pushCampaignExecutor;

  public PushCampaignDispatcher(PushCampaignRepository pushCampaignRepository,
      PushCampaignSendService pushCampaignSendService,
      @Qualifier(PushCampaignExecutorConfig.PUSH_CAMPAIGN_EXECUTOR_BEAN_NAME) Executor pushCampaignExecutor) {
    this.pushCampaignRepository = pushCampaignRepository;
    this.pushCampaignSendService = pushCampaignSendService;
    this.pushCampaignExecutor = pushCampaignExecutor;
  }

  /**
   * 선점 > 비동기 실행 (선점 실패 > false, 실행기 거절 > 선점 반납 후 false)
   */
  public boolean claimAndDispatch(UUID pushCampaignId) {
    if (!isClaimed(pushCampaignId)) {
      return false;
    }
    try {
      pushCampaignExecutor.execute(() -> pushCampaignSendService.runClaimedCampaign(pushCampaignId));
      return true;
    } catch (RejectedExecutionException rejectedException) {
      pushCampaignRepository.releaseClaimedCampaign(pushCampaignId, LocalDateTime.now());
      log.warn("[push campaign] dispatch rejected : executor busy, released for next run, pushCampaignId={}", pushCampaignId);
      return false;
    }
  }

  /**
   * 선점 > 현재 스레드에서 바로 실행 (테스트 발송: 결과를 응답에 담기 위함)
   */
  public boolean claimAndRunNow(UUID pushCampaignId) {
    if (!isClaimed(pushCampaignId)) {
      return false;
    }
    pushCampaignSendService.runClaimedCampaign(pushCampaignId);
    return true;
  }

  /**
   * 예약 시각 도래 캠페인 실행 (1분 스케줄러) > 선점 성공 수
   */
  public int dispatchDueCampaigns() {
    List<UUID> dueCampaignIds = pushCampaignRepository.findDueCampaignIds(PushCampaignStatus.SCHEDULED,
        LocalDateTime.now(), PageRequest.of(0, MAXIMUM_DUE_CAMPAIGNS_PER_RUN));
    int dispatchedCampaignCount = 0;
    for (UUID dueCampaignId : dueCampaignIds) {
      if (claimAndDispatch(dueCampaignId)) {
        dispatchedCampaignCount++;
      }
    }
    return dispatchedCampaignCount;
  }

  /**
   * 중단된 발송(오래된 SENDING) > FAILED
   */
  public int failStaleSendingCampaigns() {
    LocalDateTime now = LocalDateTime.now();
    int failedCampaignCount = pushCampaignRepository.failStaleSendingCampaigns(
        now.minus(STALE_SENDING_DURATION), STALE_SENDING_REASON, now);
    if (failedCampaignCount > 0) {
      log.warn("[push campaign] stale sending marked failed : campaigns={}", failedCampaignCount);
    }
    return failedCampaignCount;
  }

  private boolean isClaimed(UUID pushCampaignId) {
    boolean isClaimed = pushCampaignRepository.claimScheduledCampaign(pushCampaignId, LocalDateTime.now()) == 1;
    if (!isClaimed) {
      log.debug("[push campaign] claim skipped : already claimed or canceled, pushCampaignId={}", pushCampaignId);
    }
    return isClaimed;
  }
}
