# SSE 서버 로그 스트리밍 디버그 엔드포인트 설계

## 개요

테스트 빌드(APK/TestFlight)에서 앱 내 플로팅 버튼을 통해 서버의 전체 애플리케이션 로그(DEBUG/INFO/WARN/ERROR)를 실시간으로 확인할 수 있도록 SSE(Server-Sent Events) 기반 로그 스트리밍 API를 추가한다.

## 배경

- 테스트 빌드에서 서버 로그를 확인하려면 별도로 서버에 SSH 접속해야 하는 불편함
- 프론트(Flutter)에서 `TEST_BUILD_FLAG`가 활성화된 빌드에서만 플로팅 디버그 버튼을 노출
- 해당 버튼에서 서버 로그를 실시간 확인하는 기능 필요

## 프로토콜 선택: SSE

| 고려 방식 | 판단 |
|-----------|------|
| SSE (Server-Sent Events) | **채택** — 서버→클라이언트 단방향 스트림, HTTP 기반으로 Flutter에서 수신 간단 |
| WebSocket/STOMP | 기각 — 이미 채팅용 RabbitMQ STOMP 인프라가 있지만, 로그 스트리밍에는 과한 구조 |
| REST Polling | 기각 — 실시간성 부족, 빈번한 요청으로 서버 부하 |
| Syslog | 기각 — 서버 간 프로토콜, 모바일 앱에서 직접 수신 부적합 |

## 보안

기존 `@SecuredApi` (HMAC-SHA256 + Timestamp) 검증을 그대로 적용한다.

- 프론트 테스트 빌드에서 GitHub Actions secrets의 secret key를 앱에 포함
- SSE 연결 시 `X-Timestamp` + `X-Signature` 헤더로 HMAC 서명 전송
- `SecuredApiAspect` AOP가 서명 검증 수행
- 운영 빌드에는 secret key가 없으므로 호출 자체가 불가능

**관련 코드:**
- `com.romrom.common.annotation.SecuredApi` — 어노테이션
- `com.romrom.common.aop.SecuredApiAspect` — AOP 검증
- `com.romrom.common.util.SignatureUtil` — HMAC 서명 생성/검증
- `com.romrom.common.properties.SecuredApiProperties` — secret key, 만료시간 설정

## API 엔드포인트

```
GET /api/app/debug/log-stream
```

- **Content-Type**: `text/event-stream`
- **인증**: `@SecuredApi` (HMAC-SHA256)
- **타임아웃**: 5분 (300,000ms) — 클라이언트가 안 닫고 방치했을 때의 안전장치
- **종료 조건**: 5분 경과 OR 클라이언트 연결 종료

**요청 헤더:**
| 헤더 | 설명 |
|------|------|
| `X-Timestamp` | 밀리초 단위 타임스탬프 |
| `X-Signature` | HMAC-SHA256(timestamp, secretKey) Hex 인코딩 |

## SSE 이벤트 포맷

```
data: {"timestamp":"2026-03-27T14:30:00.123","level":"DEBUG","loggerName":"c.r.w.controller.api.ItemController","message":"물품 조회 요청 - memberId: 123","threadName":"http-nio-8080-exec-1"}

```

**필드 설명:**

| 필드 | 타입 | 설명 |
|------|------|------|
| `timestamp` | String | ISO 8601 형식 (yyyy-MM-dd'T'HH:mm:ss.SSS) |
| `level` | String | 로그 레벨 (DEBUG, INFO, WARN, ERROR) |
| `loggerName` | String | 로거 이름 (패키지.클래스) |
| `message` | String | 로그 메시지 |
| `threadName` | String | 스레드 이름 |

## 아키텍처

```
[Logback]
    │
    ▼
[SseLogAppender]  ── logback Appender, 로그 이벤트를 브로드캐스터에 발행
    │
    ▼
[SseLogBroadcaster]  ── 구독자(SseEmitter) 관리, 타임아웃 처리
    │
    ▼
[DebugController]  ── GET /api/app/debug/log-stream, SseEmitter 반환
    │
    ▼
[Flutter App]  ── SSE 스트림 수신, 플로팅 디버그 화면에 표시
```

### 데이터 흐름

1. 클라이언트가 `GET /api/app/debug/log-stream`으로 SSE 연결 요청
2. `@SecuredApi` AOP가 HMAC 서명 검증
3. `DebugController`가 `SseEmitter`를 생성하고 `SseLogBroadcaster`에 구독자로 등록
4. `SseEmitter`를 반환하여 SSE 연결 수립
5. 애플리케이션 로그 발생 시 `SseLogAppender`가 `SseLogBroadcaster`에 이벤트 발행
6. `SseLogBroadcaster`가 모든 활성 구독자에게 이벤트 전송
7. 5분 경과 또는 클라이언트 연결 종료 시 `SseEmitter` 완료 처리 및 구독자 제거

## 구현 파일

### 1. RomRom-Common 모듈

#### `com.romrom.common.dto.DebugLogEvent`
로그 이벤트를 담는 DTO.

```java
// 필드: timestamp, level, loggerName, message, threadName
// Jackson 직렬화를 위한 record 또는 @Value class
```

#### `com.romrom.common.service.SseLogBroadcaster`
SSE 구독자 관리 컴포넌트.

- `CopyOnWriteArrayList<SseEmitter>`로 구독자 관리
- `addSubscriber(SseEmitter)` — 구독자 등록
- `broadcast(DebugLogEvent)` — 모든 구독자에게 이벤트 전송, 전송 실패 시 해당 구독자 제거
- Spring Bean (`@Component`)으로 등록

#### `com.romrom.common.logging.SseLogAppender`
Logback 커스텀 Appender.

- `ch.qos.logback.classic.spi.ILoggingEvent`를 받아 `DebugLogEvent`로 변환
- `SseLogBroadcaster`에 이벤트 발행
- Spring ApplicationContext에서 `SseLogBroadcaster` 빈을 가져와서 사용
- `com.romrom` 패키지 로그만 필터링 (프레임워크 로그 제외)

### 2. RomRom-Web 모듈

#### `com.romrom.web.controller.api.DebugController`
SSE 스트리밍 엔드포인트.

```java
@GetMapping(value = "/debug/log-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@SecuredApi
public SseEmitter streamDebugLog() {
    // SseEmitter 생성 (timeout: 300,000ms)
    // SseLogBroadcaster에 구독자 등록
    // onCompletion, onTimeout, onError 콜백에서 구독자 제거
    // SseEmitter 반환
}
```

- `@RequestMapping("/api/app")` — 기존 AppConfigController와 동일한 prefix
- `@Tag(name = "디버그 API")` — Swagger 그룹 분리

#### `com.romrom.web.controller.api.DebugControllerDocs`
Swagger 문서화 인터페이스.

- `@Operation` description에 HMAC 인증 방식, SSE 포맷, 타임아웃 설명
- `@ApiChangeLogs` 최상단에 신규 API 추가 로그

### 3. 설정 변경

#### `SecurityUrls.java`
```java
SECURED_API_URLS에 추가:
"/api/app/debug/log-stream"
```

#### `logback-spring.xml` (신규 생성)
- 기존 application.yml의 logging 설정은 유지
- `SseLogAppender`를 추가 Appender로 등록
- `com.romrom` 로거에 `SseLogAppender` 연결

## 제약사항 및 고려사항

### 동시 접속 제한
- `SseLogBroadcaster`에서 최대 구독자 수 제한 (기본값: 5)
- 초과 시 가장 오래된 연결 종료 또는 새 연결 거부

### @SecuredApi와 SSE 호환성
- `@SecuredApi`는 `HttpServletRequest`에서 헤더를 읽는 AOP
- SSE 초기 연결은 일반 HTTP GET 요청이므로 AOP 적용에 문제 없음
- 한번 연결되면 이후는 서버 → 클라이언트 스트리밍이므로 추가 인증 불필요

### 로그 폭주 대응
- `SseLogAppender`에서 초당 로그 이벤트 수 제한 (rate limiting)
- 제한 초과 시 로그 건너뛰고 "[N건 생략]" 메시지 전송
- 기본값: 초당 100건

### 운영 환경 안전장치
- `@SecuredApi` HMAC 검증으로 인증되지 않은 접근 차단
- 운영 빌드에는 secret key가 포함되지 않으므로 호출 자체 불가능
- 추가로 Spring Profile 기반 비활성화를 고려할 수 있으나, HMAC만으로 충분

## 연관 이슈

- `.issue/#20260327_002_기능추가_SSE_서버로그_스트리밍_디버그_엔드포인트.md`
- 프론트(RomRom-FE) 측: 테스트 빌드 `TEST_BUILD_FLAG` 플로팅 디버그 버튼 구현 (별도 이슈)
