package com.romrom.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * 에러 알림 자동 구성: 비활성 기본값, 활성 시 log.error > Discord 웹훅 전송
 * 가짜 웹훅 서버로 실제 HTTP 전송까지 확인 (DB·외부망 불필요)
 */
class ErrorAlertWiringTest {

  // 웹훅 전송 대기 상한 (워커 비동기 처리)
  private static final long WEBHOOK_WAIT_SECONDS = 10;
  // 스타터가 autoconfigure를 runtime 스코프로 제공 > 컴파일 참조 불가, 이름으로 로드
  private static final String ERROR_ALERT_AUTO_CONFIGURATION_CLASS_NAME =
      "io.github.chuseok22.erroralert.autoconfigure.ErrorAlertAutoConfiguration";
  private static final String ERROR_ALERT_QUEUE_BEAN_NAME = "errorAlertQueue";

  private WebApplicationContextRunner contextRunner;

  private HttpServer fakeDiscordServer;
  private final BlockingQueue<String> receivedWebhookBodies = new LinkedBlockingQueue<>();

  @BeforeEach
  void startFakeDiscordServer() throws Exception {
    contextRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(Class.forName(ERROR_ALERT_AUTO_CONFIGURATION_CLASS_NAME)));
    fakeDiscordServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    fakeDiscordServer.createContext("/webhook", exchange -> {
      try (InputStream requestBody = exchange.getRequestBody()) {
        receivedWebhookBodies.add(new String(requestBody.readAllBytes(), StandardCharsets.UTF_8));
      }
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    fakeDiscordServer.start();
  }

  @AfterEach
  void stopFakeDiscordServer() {
    fakeDiscordServer.stop(0);
  }

  @Test
  @DisplayName("ERROR_ALERT_ENABLED 미주입(기본 false)이면 에러 알림 빈이 생성되지 않는다")
  void 비활성이면_에러_알림_빈이_없다() {
    contextRunner
        .withPropertyValues("error-alert.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ERROR_ALERT_QUEUE_BEAN_NAME));
  }

  @Test
  @DisplayName("llm.enabled=false는 LlmClient 빈 누락으로 기동 실패 — application.yml에 두지 않는 근거")
  void llm_enabled_false면_기동에_실패한다() {
    contextRunner
        .withPropertyValues("error-alert.enabled=true", "error-alert.llm.enabled=false")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("활성화하면 log.error가 Discord 웹훅으로 전송된다")
  void 활성이면_log_error가_웹훅으로_전송된다() {
    String fakeWebhookUrl = "http://127.0.0.1:" + fakeDiscordServer.getAddress().getPort() + "/webhook";

    contextRunner
        .withPropertyValues(
            "error-alert.enabled=true",
            "error-alert.environment=test",
            "error-alert.application.name=RomRom-BE",
            "error-alert.discord.enabled=true",
            "error-alert.discord.webhook-url=" + fakeWebhookUrl)
        .run(context -> {
          assertThat(context).hasBean(ERROR_ALERT_QUEUE_BEAN_NAME);

          Logger testLogger = LoggerFactory.getLogger(ErrorAlertWiringTest.class);
          testLogger.error("[error alert wiring] test error", new IllegalStateException("wiring check"));

          String webhookBody = receivedWebhookBodies.poll(WEBHOOK_WAIT_SECONDS, TimeUnit.SECONDS);
          assertThat(webhookBody).as("웹훅 미수신").isNotNull();
          assertThat(webhookBody).contains("RomRom-BE").contains("wiring check");
        });
  }
}
