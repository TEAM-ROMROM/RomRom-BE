package com.romrom.notification.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 푸시 캠페인 실행 전용 Executor: 대량 발송이 공용 @Async 풀·스케줄러 스레드를 막지 않게 분리 (#874)
 */
@Configuration
public class PushCampaignExecutorConfig {

  public static final String PUSH_CAMPAIGN_EXECUTOR_BEAN_NAME = "pushCampaignExecutor";
  // 동시 실행 캠페인 수 (FCM 호출 병렬도 제한)
  private static final int CAMPAIGN_POOL_SIZE = 2;
  // 대기 캠페인 상한 (초과 > 선점 반납 후 다음 주기 재시도)
  private static final int CAMPAIGN_QUEUE_CAPACITY = 100;
  // 종료 시 진행 중 캠페인 대기 (초)
  private static final int AWAIT_TERMINATION_SECONDS = 60;

  @Bean(name = PUSH_CAMPAIGN_EXECUTOR_BEAN_NAME)
  public Executor pushCampaignExecutor() {
    ThreadPoolTaskExecutor pushCampaignExecutor = new ThreadPoolTaskExecutor();
    pushCampaignExecutor.setCorePoolSize(CAMPAIGN_POOL_SIZE);
    pushCampaignExecutor.setMaxPoolSize(CAMPAIGN_POOL_SIZE);
    pushCampaignExecutor.setQueueCapacity(CAMPAIGN_QUEUE_CAPACITY);
    pushCampaignExecutor.setThreadNamePrefix("push-campaign-");
    pushCampaignExecutor.setWaitForTasksToCompleteOnShutdown(true);
    pushCampaignExecutor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
    pushCampaignExecutor.initialize();
    return pushCampaignExecutor;
  }
}
