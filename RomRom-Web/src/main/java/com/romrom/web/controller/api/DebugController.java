package com.romrom.web.controller.api;

import com.romrom.common.annotation.SecuredApi;
import com.romrom.common.service.SseLogBroadcaster;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DebugController implements DebugControllerDocs {

  private static final long SSE_LOG_STREAM_TIMEOUT = 300_000L; // 5분

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

    // 연결 종료 시 구독자 제거 (onTimeout/onError → complete() → onCompletion 순서로 호출됨)
    debugLogEmitter.onCompletion(() -> {
      sseLogBroadcaster.removeSubscriber(debugLogEmitter);
      log.debug("SSE 로그 스트리밍 연결 종료");
    });
    debugLogEmitter.onTimeout(() -> {
      log.debug("SSE 로그 스트리밍 타임아웃 (5분)");
      debugLogEmitter.complete();
    });
    debugLogEmitter.onError(throwable -> {
      log.debug("SSE 로그 스트리밍 에러: {}", throwable.getMessage());
      debugLogEmitter.complete();
    });

    log.info("SSE 로그 스트리밍 연결 수립 (활성 구독자: {}명)", sseLogBroadcaster.getActiveSubscriberCount());

    return debugLogEmitter;
  }
}
