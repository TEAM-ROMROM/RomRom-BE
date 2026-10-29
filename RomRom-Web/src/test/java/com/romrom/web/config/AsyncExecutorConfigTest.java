package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async 기본 실행기 검증 (#820): 상한 있는 풀, 포화 시 호출 스레드 실행
 */
class AsyncExecutorConfigTest {

  @Test
  @DisplayName("기본 실행기는 상한 있는 ThreadPoolTaskExecutor")
  void 기본_실행기는_상한_있는_풀이다() {
    ThreadPoolTaskExecutor asyncExecutor = assertInstanceOf(ThreadPoolTaskExecutor.class,
        new AsyncExecutorConfig().getAsyncExecutor());
    try {
      assertEquals(AsyncExecutorConfig.CORE_POOL_SIZE, asyncExecutor.getCorePoolSize());
      assertEquals(AsyncExecutorConfig.MAX_POOL_SIZE, asyncExecutor.getMaxPoolSize());
      assertEquals(AsyncExecutorConfig.QUEUE_CAPACITY, asyncExecutor.getQueueCapacity());
      assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class,
          asyncExecutor.getThreadPoolExecutor().getRejectedExecutionHandler());
    } finally {
      asyncExecutor.shutdown();
    }
  }

  @Test
  @DisplayName("작업은 async- 접두사 스레드에서 실행")
  void 작업은_풀_스레드에서_실행된다() throws InterruptedException {
    ThreadPoolTaskExecutor asyncExecutor = (ThreadPoolTaskExecutor) new AsyncExecutorConfig().getAsyncExecutor();
    AtomicReference<String> executedThreadName = new AtomicReference<>();
    CountDownLatch executedLatch = new CountDownLatch(1);
    try {
      asyncExecutor.execute(() -> {
        executedThreadName.set(Thread.currentThread().getName());
        executedLatch.countDown();
      });
      assertTrue(executedLatch.await(5, TimeUnit.SECONDS));
      assertTrue(executedThreadName.get().startsWith(AsyncExecutorConfig.THREAD_NAME_PREFIX));
    } finally {
      asyncExecutor.shutdown();
    }
  }
}
