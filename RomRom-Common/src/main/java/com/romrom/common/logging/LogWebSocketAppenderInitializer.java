package com.romrom.common.logging;

import java.util.logging.Handler;
import java.util.logging.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Context 초기화 시 실시간 로그 스트리밍 컴포넌트에 ApplicationContext를 주입하고,
 * suhlogger(JUL) 로거에 WebSocket 브리지 핸들러를 연결한다.
 *
 * <ul>
 *   <li>{@link LogWebSocketAppender}: logback 일반 로그 → broadcaster (Spring 빈이 아니므로 정적 주입)</li>
 *   <li>{@link JulToWebSocketBridge}: suhlogger JUL 로그(@LogMonitor 등) → broadcaster (AOP 토글용)</li>
 * </ul>
 */
@Component
public class LogWebSocketAppenderInitializer implements ApplicationListener<ContextRefreshedEvent> {

  // suhlogger가 사용하는 JUL 로거 이름 (SuhLoggerConfig 내부 상수와 동일)
  private static final String SUHLOGGER_JUL_NAME = "me.suhsaechan.suhlogger";

  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
    ApplicationContext applicationContext = contextRefreshedEvent.getApplicationContext();

    // logback appender에 Context 주입
    LogWebSocketAppender.setApplicationContext(applicationContext);

    // JUL 브리지에 Context 주입 + suhlogger 로거에 핸들러 연결
    JulToWebSocketBridge.setApplicationContext(applicationContext);
    attachJulBridge();
  }

  /**
   * suhlogger JUL 로거에 브리지 핸들러를 1회만 연결 (ContextRefreshed가 중복 발생해도 idempotent).
   * suhlogger는 setUseParentHandlers(false)라 부모 핸들러로는 안 잡히므로 해당 로거에 직접 붙인다.
   */
  private void attachJulBridge() {
    Logger suhLoggerJul = Logger.getLogger(SUHLOGGER_JUL_NAME);
    for (Handler existingHandler : suhLoggerJul.getHandlers()) {
      if (existingHandler instanceof JulToWebSocketBridge) {
        return; // 이미 연결됨
      }
    }
    suhLoggerJul.addHandler(new JulToWebSocketBridge());
  }
}
