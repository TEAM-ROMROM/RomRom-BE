package com.romrom.web.config;

import com.romrom.common.service.ActivityEventRecorder;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 활동 이벤트 적재 전용 실행기: 작은 풀 + 큐 포화 시 버림 (지표 유실 허용, 요청 스레드 보호)
 */
@Configuration
public class ActivityEventExecutorConfig {

  static final int CORE_POOL_SIZE = 1;
  static final int MAX_POOL_SIZE = 2;
  // 대기 이벤트 상한 (초과분은 버림)
  static final int QUEUE_CAPACITY = 2000;
  static final String THREAD_NAME_PREFIX = "activity-event-";

  @Bean(name = ActivityEventRecorder.ACTIVITY_EVENT_EXECUTOR_BEAN_NAME)
  public Executor activityEventExecutor() {
    ThreadPoolTaskExecutor activityEventExecutor = new ThreadPoolTaskExecutor();
    activityEventExecutor.setCorePoolSize(CORE_POOL_SIZE);
    activityEventExecutor.setMaxPoolSize(MAX_POOL_SIZE);
    activityEventExecutor.setQueueCapacity(QUEUE_CAPACITY);
    activityEventExecutor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    activityEventExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    activityEventExecutor.initialize();
    return activityEventExecutor;
  }
}
