package com.romrom.application.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Admin 360 카드 병렬 조회 전용 Executor 빈 설정
 */
@Configuration
public class AdminAsyncConfig {

  @Bean(name = "adminMemberDetailExecutor")
  public Executor adminMemberDetailExecutor() {
    ThreadPoolTaskExecutor adminMemberDetailExecutor = new ThreadPoolTaskExecutor();
    adminMemberDetailExecutor.setCorePoolSize(8);
    adminMemberDetailExecutor.setMaxPoolSize(16);
    adminMemberDetailExecutor.setQueueCapacity(100);
    adminMemberDetailExecutor.setThreadNamePrefix("admin-member-360-");
    adminMemberDetailExecutor.initialize();
    return adminMemberDetailExecutor;
  }
}
