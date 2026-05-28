package com.romrom.storage.config;

import com.romrom.common.service.SystemConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 이미지 업로드 전용 스레드풀 설정
 * 풀 크기는 SystemConfig(image.upload.parallel-pool-size)에서 부팅 시 1회 결정한다.
 * (변경 반영은 서버 재시작 시점)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ImageUploadExecutorConfig {

  private static final String KEY_PARALLEL_POOL_SIZE = "image.upload.parallel-pool-size";
  private static final String DEFAULT_PARALLEL_POOL_SIZE = "8";

  private final SystemConfigCacheService systemConfigCacheService;

  @Bean(name = "imageUploadExecutor", destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor imageUploadExecutor() {
    int poolSize = resolvePoolSize();
    ThreadPoolTaskExecutor imageUploadExecutor = new ThreadPoolTaskExecutor();
    imageUploadExecutor.setCorePoolSize(poolSize);
    imageUploadExecutor.setMaxPoolSize(poolSize);
    imageUploadExecutor.setQueueCapacity(100);
    imageUploadExecutor.setThreadNamePrefix("img-upload-");
    imageUploadExecutor.initialize();
    log.info("이미지 업로드 스레드풀 초기화: poolSize={}", poolSize);
    return imageUploadExecutor;
  }

  private int resolvePoolSize() {
    String rawPoolSize = systemConfigCacheService.getOrDefault(KEY_PARALLEL_POOL_SIZE, DEFAULT_PARALLEL_POOL_SIZE);
    try {
      int parsedPoolSize = Integer.parseInt(rawPoolSize.trim());
      return parsedPoolSize > 0 ? parsedPoolSize : Integer.parseInt(DEFAULT_PARALLEL_POOL_SIZE);
    } catch (NumberFormatException e) {
      log.warn("image.upload.parallel-pool-size 파싱 실패, 기본값 사용: {}", rawPoolSize);
      return Integer.parseInt(DEFAULT_PARALLEL_POOL_SIZE);
    }
  }
}
