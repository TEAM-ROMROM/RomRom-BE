package com.romrom.common.logging;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Context 초기화 시 LogWebSocketAppender에 ApplicationContext를 주입
 * (logback Appender는 Spring 빈이 아니므로 정적 메서드로 Context를 넘겨준다)
 */
@Component
public class LogWebSocketAppenderInitializer implements ApplicationListener<ContextRefreshedEvent> {

  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
    ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();
    LogWebSocketAppender.setApplicationContext(applicationContext);
  }
}
