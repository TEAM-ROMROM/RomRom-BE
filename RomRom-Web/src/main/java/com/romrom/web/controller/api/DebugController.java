package com.romrom.web.controller.api;

import com.romrom.common.annotation.SecuredApi;
import com.romrom.common.service.SseLogBroadcaster;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 디버그용 SSE 로그 스트리밍 엔드포인트
 * 테스트 빌드에서 앱 내 플로팅 버튼으로 서버 로그를 실시간 확인
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "디버그 API", description = "테스트 빌드용 디버그 도구 API")
@RequestMapping("/api/app")
public class DebugController implements DebugControllerDocs {

  private static final long SSE_LOG_STREAM_TIMEOUT = 300_000L; // 5분
  private static final long SSE_HEARTBEAT_INTERVAL_SECONDS = 30L;

  private static final ScheduledExecutorService heartbeatScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread heartbeatThread = new Thread(r, "sse-heartbeat");
        heartbeatThread.setDaemon(true);
        return heartbeatThread;
      });

  private final SseLogBroadcaster sseLogBroadcaster;

  @Override
  @GetMapping(value = "/debug/log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @SecuredApi
  public SseEmitter streamDebugLog() {
    SseEmitter debugLogEmitter = new SseEmitter(SSE_LOG_STREAM_TIMEOUT);

    boolean isSubscriberRegistered = sseLogBroadcaster.addSubscriber(debugLogEmitter);
    if (!isSubscriberRegistered) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "최대 동시 접속 수 초과");
    }

    // 연결 수립 즉시 connected 이벤트 전송 — iOS URLSession이 빈 body로 판단해 끊는 것 방지
    try {
      debugLogEmitter.send(SseEmitter.event().name("connected").data("connected"));
    } catch (IOException e) {
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      debugLogEmitter.complete();
      return debugLogEmitter;
    }

    // 30초마다 heartbeat comment 전송 — idle timeout 방지
    // AtomicReference: 람다 내부에서 heartbeatTask 자기 자신을 참조하기 위한 전방 참조 처리
    AtomicReference<ScheduledFuture<?>> heartbeatTaskRef = new AtomicReference<>();
    ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
      try {
        debugLogEmitter.send(SseEmitter.event().comment("heartbeat"));
      } catch (IOException e) {
        ScheduledFuture<?> selfHeartbeatTask = heartbeatTaskRef.get();
        if (selfHeartbeatTask != null) {
          selfHeartbeatTask.cancel(false);
        }
        sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      }
    }, SSE_HEARTBEAT_INTERVAL_SECONDS, SSE_HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    heartbeatTaskRef.set(heartbeatTask);

    debugLogEmitter.onCompletion(() -> {
      heartbeatTask.cancel(false);
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
    });
    debugLogEmitter.onTimeout(() -> {
      heartbeatTask.cancel(false);
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      debugLogEmitter.complete();
    });
    debugLogEmitter.onError(throwable -> {
      heartbeatTask.cancel(false);
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      debugLogEmitter.complete();
    });

    return debugLogEmitter;
  }
}
