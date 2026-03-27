package com.romrom.common.logging;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Context 초기화 시 SseLogAppender에 ApplicationContext를 주입
 */
@Component
public class SseLogAppenderInitializer implements ApplicationListener<ContextRefreshedEvent> {

  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
    ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
    SseLogAppender.setApplicationContext(applicationContext);
  }
}
