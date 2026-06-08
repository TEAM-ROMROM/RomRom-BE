package com.romrom.web.controller.api;

import com.romrom.common.dto.Author;
import io.swagger.v3.oas.annotations.Operation;
import me.suhsaechan.suhapilog.annotation.ApiChangeLog;
import me.suhsaechan.suhapilog.annotation.ApiChangeLogs;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface DebugControllerDocs {

  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.06.08", author = Author.SUHSAECHAN, issueNumber = 788, description = "리버스 프록시 SSE 버퍼링 해제(X-Accel-Buffering: no) 헤더 추가, timeout 50초로 단축(프록시 read timeout 대비)"),
      @ApiChangeLog(date = "2026.03.27", author = Author.SUHSAECHAN, issueNumber = 607, description = "SSE 서버 로그 스트리밍 디버그 엔드포인트 추가"),
  })
  @Operation(
      summary = "서버 로그 실시간 스트리밍 (SSE)",
      description = """
      ## 인증: **@SecuredApi** (HMAC + Timestamp)

      ## 요청 헤더
      - **`X-Timestamp`**: 밀리초 단위 타임스탬프
      - **`X-Signature`**: HMAC-SHA256(timestamp, secretKey) Hex 인코딩

      ## 반환값
      - **Content-Type**: `text/event-stream`
      - SSE 스트림으로 서버 로그 이벤트를 실시간 전송

      ## SSE 이벤트 포맷
      ```json
      {
        "timestamp": "2026-03-27T14:30:00.123",
        "level": "DEBUG",
        "loggerName": "com.romrom.web.controller.api.ItemController",
        "message": "물품 조회 요청 - memberId: 123",
        "threadName": "http-nio-8080-exec-1"
      }
      ```

      ## 동작 설명
      - 연결 후 서버의 전체 애플리케이션 로그(DEBUG/INFO/WARN/ERROR)를 실시간 스트리밍
      - com.romrom 패키지 로그만 전송 (프레임워크 로그 제외)
      - 50초 후 자동 연결 종료 → 클라이언트가 재연결 (리버스 프록시 read timeout 대비)
      - 응답에 `X-Accel-Buffering: no` 헤더 → nginx 버퍼링 비활성화(즉시 전달)
      - 클라이언트가 연결을 종료하면 즉시 정리
      - 최대 동시 접속: 10명
      - 초당 최대 100건 (초과 시 "[N건 생략]" 메시지 전송)

      ## 에러코드
      - MISSING_SIGNATURE_HEADER (401): 서명 헤더 누락
      - EXPIRED_SIGNATURE_TIMESTAMP (401): 타임스탬프 만료
      - INVALID_SIGNATURE (401): 유효하지 않은 서명
      - 503: 최대 동시 접속 초과
      """
  )
  ResponseEntity<SseEmitter> streamDebugLog();
}
