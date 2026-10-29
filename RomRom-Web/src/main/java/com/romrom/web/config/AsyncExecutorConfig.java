package com.romrom.web.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이름 없는 @Async 기본 실행기: 상한 있는 공용 풀 (알림·임베딩·이력 적재 등)
 * 전용 Executor 빈이 있으면 Boot 기본 풀이 꺼져 SimpleAsyncTaskExecutor(요청마다 새 스레드)로 떨어지므로 직접 지정 (#820)
 */
@Configuration
public class AsyncExecutorConfig implements AsyncConfigurer {

  static final int CORE_POOL_SIZE = 4;
  static final int MAX_POOL_SIZE = 16;
  // 대기 작업 상한 (초과 시 호출 스레드에서 실행 > 유실 없음)
  static final int QUEUE_CAPACITY = 500;
  // 종료 시 남은 작업 대기 (초)
  static final int AWAIT_TERMINATION_SECONDS = 30;
  static final String THREAD_NAME_PREFIX = "async-";

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor asyncExecutor = new ThreadPoolTaskExecutor();
    asyncExecutor.setCorePoolSize(CORE_POOL_SIZE);
    asyncExecutor.setMaxPoolSize(MAX_POOL_SIZE);
    asyncExecutor.setQueueCapacity(QUEUE_CAPACITY);
    asyncExecutor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    asyncExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    asyncExecutor.setWaitForTasksToCompleteOnShutdown(true);
    asyncExecutor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
    asyncExecutor.initialize();
    return asyncExecutor;
  }
}
