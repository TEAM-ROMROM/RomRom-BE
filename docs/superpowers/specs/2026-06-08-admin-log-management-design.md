# 관리자 로그 관리 화면 설계

- 작성일: 2026-06-08
- 작성자: SUH SAECHAN
- 상태: 설계 확정 (구현 대기)

## 1. 배경 / 목적

운영 중 서버 로그를 확인하려면 매번 SSH로 서버에 접속해 `/mnt/romrom/logs/` 경로로 들어가서
`tail`, `grep`, `zcat`, `scp` 등을 직접 수행해야 한다. 이 과정을 전부 **관리자 웹 화면(`/admin/logs`)에서
끝낼 수 있도록** 한다.

핵심 원칙:
- 로그는 **이미 파일로 쌓이고 있으므로**(`romrom.log` + 날짜별 `.gz` 롤링), 그 **파일을 읽어서** 화면에 제공한다.
  별도 DB 적재는 하지 않는다.
- 서버에 들어가서 하던 행위(tail -f / tail / grep / ls / scp / zcat | grep / df)를 화면 기능으로 1:1 대응시킨다.
- **브라우저 부하를 최소화**한다. 로그가 쏟아져도 브라우저가 뻗지 않아야 한다.

### 기존 자산 / 통합 대상

이미 SSE 기반 디버그 로그 스트림이 존재한다(2026-03-27 설계). 단, 용도가 다르고 현재 정상 동작하지 않는 상태다.

- `RomRom-Common/.../logging/SseLogAppender.java` — `com.romrom.*` 로그를 SSE 구독자에게 발행하는 Logback Appender
- `RomRom-Common/.../logging/SseLogAppenderInitializer.java` — Appender에 ApplicationContext 주입
- `RomRom-Common/.../service/SseLogBroadcaster.java` — 구독자 관리 + 브로드캐스트 (최대 10명, 초당 100건 rate limit)
- `RomRom-Web/.../controller/api/DebugController.java` — `GET /api/app/debug/log-stream` (앱 테스트 빌드 플로팅 버튼용)
- `RomRom-Common/.../dto/DebugLogEvent.java` — 로그 이벤트 DTO
- `logback-spring.xml` — `SSE_LOG` appender가 root logger에 등록됨

**결정: 이 SSE 인프라를 이번 관리자 화면으로 통합한다.** 앱 전용 디버그 엔드포인트(`/api/app/debug/log-stream`)는
관리자 인증 경로(`/api/admin/logs/stream`)로 일원화하고, 기존 Broadcaster/Appender/Event DTO는 재활용한다.

## 2. 범위

### 포함 (이번 작업)

1. **조회·검색 탭** — 현재 `romrom.log`를 최신순으로 조회. 레벨 필터(ERROR/WARN/INFO/DEBUG) + 키워드 검색 + 줄수 선택.
2. **에러 대시보드 탭** — 최근 N분 ERROR/WARN을 예외 클래스별로 집계(발생횟수 / 마지막 발생시각 / 대표 메시지).
3. **실시간 탭** — `tail -f` 처럼 라이브 출력. 브라우저 부하 최소화 장치 포함.
4. **파일 목록 탭** — 로그 디렉터리 스캔: 현재 `.log` + 과거 `.gz` 목록(파일명/크기/수정시각). 로그 총 용량/디스크 상태 표시.
5. **다운로드** — ① 현재 로그 시간범위(최근 5분/1h/6h/24h) ② 파일 통째(`.log` 및 각 `.gz`).
6. **.gz 조회·검색** — 과거 `.gz` 파일을 서버에서 압축 해제 후 화면에서 조회/키워드 검색 (`zcat | grep` 대체).
7. **공통** — 관리자 인증 필수, path traversal 방어.

### 제외 (YAGNI)

- ❌ DB 로그 적재 — 파일로 충분.
- ❌ `.gz` 파일에 대한 **시간범위 다운로드** — `.gz`는 파일 통째 다운로드만. (시간범위는 현재 `.log`만)
- ❌ 로그 민감정보 자동 마스킹 — 관리자 인증으로 접근 통제. 마스킹은 후속 과제.
- ❌ 로그 파일 삭제 기능 — 위험도 높아 이번 범위에서 제외.
- ❌ 다중 서버 로그 통합 — 접속한 인스턴스의 파일만. (단일 서버 전제. 멀티 인스턴스라면 "그 인스턴스의 파일만 보임" 한계 존재)

## 3. 아키텍처

```
[romrom.log / *.gz 파일]                  [Logback SseLogAppender (기존)]
   /mnt/romrom/logs/                              |
        |                                         v
        v                                  [SseLogBroadcaster (기존 재활용)]
[LogFileService] <- [LogLineParser]               |
        |                                         |
        v                                         v
[AdminApiController] ----------------------> [SSE: /api/admin/logs/stream]
        |                                         |
        v                                         v
[admin/logs.html (Thymeleaf + 바닐라 JS, DaisyUI 탭 4개)]
```

데이터 소스는 **파일 직접 읽기**. 실시간만 기존 SSE Broadcaster를 재활용한다.

## 4. 컴포넌트 상세

### (A) `LogFileService` (신규) — `RomRom-Application` / `com.romrom.application.service`

> Admin Service는 RomRom-Application 모듈에 둔다는 프로젝트 컨벤션 준수.

로그 디렉터리 경로는 하드코딩하지 않고 설정에서 주입:
`@Value("${logging.file.name}")` 로 `romrom.log` 풀경로를 받아 부모 디렉터리를 도출한다.

메서드:

| 메서드 | 설명 | 사용처 |
|---|---|---|
| `readRecentLines(int lineCount, String level, String keyword)` | 현재 `.log` 파일 끝에서부터 역방향으로 N줄 읽어 레벨/키워드 필터 적용. **전체 메모리 로드 금지** — `RandomAccessFile` 기반 tail. | 조회·검색 탭 |
| `aggregateErrors(int withinMinutes)` | 최근 N분 ERROR/WARN 라인 파싱 → `예외클래스명 → {발생횟수, 마지막발생시각, 대표메시지}` 집계. 최신순 정렬. | 에러 대시보드 탭 |
| `listLogFiles()` | 로그 디렉터리 스캔 → `romrom.log` + `romrom.log.*.gz` 목록 `{파일명, 크기, 수정시각}` 최신순. + 총 용량/파일 수/(가능 시)디스크 여유공간. | 파일 목록 탭 |
| `extractByTimeRange(Duration range)` | 현재 `.log`에서 최근 5분/1h/6h/24h 라인만 잘라 텍스트 반환. 타임스탬프 파싱 기반. | 시간범위 다운로드 |
| `getLogFileResource(String fileName)` | 화이트리스트 검증 후 지정 파일을 `Resource` 스트리밍. `.log`/`.gz` 통째 다운로드. | 파일 다운로드 |
| `readGzLines(String fileName, int lineCount, String level, String keyword)` | 지정 `.gz` 파일을 `GZIPInputStream`으로 풀어 레벨/키워드 필터 적용한 라인 반환. 줄수 캡 적용. | .gz 조회·검색 탭 |

### (B) `LogLineParser` (신규, 작은 유틸) — `RomRom-Application` 또는 `RomRom-Common`

로그 한 줄을 `{timestamp, level, logger, message}` 로 파싱.

- 로그 포맷은 `logback-spring.xml`의 `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n` 와 1:1.
  파싱 패턴은 **상수로 분리**(하드코딩 금지). 변수명도 서술적으로(`LOG_LINE_PATTERN` 등).
- 멀티라인 스택트레이스(타임스탬프로 시작하지 않는 라인)는 **직전 로그 라인의 message에 이어붙인다.**

### (C) `AdminApiController` 엔드포인트 추가 — `RomRom-Web`

기존 admin 컨벤션 준수: **`POST` + `multipart/form-data`(`@ModelAttribute AdminRequest`) → `AdminResponse`**.
단, 파일/스트림 응답은 성격상 예외(GET 또는 SSE).

| 엔드포인트 | 메서드 | 설명 |
|---|---|---|
| `/api/admin/logs/query` | POST | 현재 로그 조회/검색 (lineCount, level, keyword) |
| `/api/admin/logs/errors` | POST | 에러 집계 (withinMinutes) |
| `/api/admin/logs/files` | POST | 파일 목록 + 용량/디스크 상태 |
| `/api/admin/logs/gz-query` | POST | 특정 `.gz` 조회/검색 (fileName, lineCount, level, keyword) |
| `/api/admin/logs/download` | GET | 시간범위 다운로드 (range: `5m`/`1h`/`6h`/`24h`) — 파일 응답 |
| `/api/admin/logs/download-file` | GET | 특정 파일 통째 다운로드 (fileName) — 파일 응답 |
| `/api/admin/logs/stream` | GET(SSE) | 실시간 라이브 tail. 기존 `SseLogBroadcaster` 재활용 |

### (D) `AdminRequest` / `AdminResponse` 필드 추가

도메인별 DTO를 만들지 않는다는 컨벤션에 따라 기존 `AdminRequest`/`AdminResponse`에 필드 추가.

`AdminRequest` 추가 (서술적 이름):
- `Integer logLineCount` — 조회 줄 수
- `String logLevelFilter` — 레벨 필터 (ERROR/WARN/INFO/DEBUG/전체)
- `String logKeyword` — 키워드 검색어
- `Integer logErrorWithinMinutes` — 에러 집계 기간(분)
- `String logFileName` — 대상 파일명 (.gz 조회/다운로드용)

`AdminResponse` 추가:
- `List<String> logLines` — 조회/검색 결과 라인
- `List<AdminLogErrorSummary> logErrorSummaries` — 에러 집계 (내부 정적 클래스: `exceptionClassName`, `occurrenceCount`, `lastOccurredAt`, `representativeMessage`)
- `List<AdminLogFileInfo> logFiles` — 파일 목록 (내부 정적 클래스: `fileName`, `fileSizeBytes`, `lastModifiedAt`)
- `Long logTotalSizeBytes` — 로그 총 용량
- `Integer logFileCount` — 파일 개수
- `Long diskFreeBytes` / `Long diskTotalBytes` — 디스크 상태 (조회 가능 시)
- `Integer logSkippedLineCount` — 줄수 캡으로 잘린 건수 (있으면 UI에 표시)

### (E) `AdminPageController` 페이지 추가 — `RomRom-Web`

- `GET /admin/logs` → `admin/logs.html`. `pageTitle="로그 관리"`, `currentMenu="logs"`.
- `layout.html` 사이드바에 "로그 관리" 메뉴 추가 (`scroll-text` Lucide 아이콘), 설정 메뉴 위 또는 아래.

### (F) `admin/logs.html` (신규 Thymeleaf)

기존 admin 페이지들과 동일 톤(Tailwind 4 + DaisyUI 5 + Lucide + 바닐라 JS). DaisyUI 탭 4개:

```
┌─ 로그 관리 ─────────────────────────────────────────────┐
│ [조회·검색] [에러 대시보드] [실시간] [파일/다운로드]      │
├─────────────────────────────────────────────────────────┤
│ ▶ 조회·검색                                              │
│   레벨[전체▾] 키워드[______] 줄수[200▾] [검색]            │
│   (모노스페이스 출력, 레벨별 색상: ERROR 빨강/WARN 노랑)  │
│                                                          │
│ ▶ 에러 대시보드                                          │
│   기간[최근 1시간▾]                                      │
│   예외클래스 | 횟수 | 마지막발생 | 대표메시지            │
│   (행 클릭 → 조회 탭으로 키워드 점프)                     │
│                                                          │
│ ▶ 실시간                                                 │
│   [● 연결/끊김] 자동스크롤[✓] [지우기]                    │
│   (SSE 라이브, DOM 500줄 캡)                              │
│                                                          │
│ ▶ 파일/다운로드                                          │
│   [최근 5분][최근 1시간][최근 6시간][최근 24시간] 다운    │
│   로그 총 용량: 57 MB / 디스크 여유: 12 GB                │
│   파일목록: 파일명 | 크기 | 수정시각 | [⬇ 다운][🔍 조회]  │
│     · .gz 행의 [🔍 조회] → gz-query 모달로 압축 풀어 조회 │
└──────────────────────────────────────────────────────────┘
```

## 5. 브라우저 부하 최소화 (핵심 요구사항)

**실시간 탭:**
- **DOM 노드 상한 500줄** — ring buffer. 초과 시 오래된 줄부터 제거. (브라우저 뻗는 주원인 차단)
- **탭 활성 시에만 SSE 연결** — 실시간 탭을 벗어나거나 브라우저 탭이 백그라운드(`visibilitychange`)면 SSE close,
  복귀 시 재연결. 안 보는 동안 네트워크/메모리 미사용.
- **append 배칭** — 들어온 로그를 `requestAnimationFrame`으로 묶어 일괄 DOM 삽입 (reflow 폭주 방지).
- **자동스크롤은 ON일 때만**, 사용자가 위로 스크롤하면 자동 일시정지.
- 서버측 rate limit(기존 초당 100건) 유지 → 폭주 시 "[N건 생략]" 표시.

**조회/검색 탭:**
- 기본 200줄, 상한 캡(예: 2000줄). 무한 로드 금지. 초과분은 `logSkippedLineCount`로 표시.

**.gz 조회:**
- 압축 해제는 **서버에서** 수행, 필요한 라인만 전송. 브라우저로 gz 통째 전송 금지. 줄수 캡 적용.

**의존성:**
- 새 JS 라이브러리 도입 없음. 기존 admin 스택(바닐라 JS)만. 가상스크롤 등 무거운 뷰어 미사용 — DOM 상한으로 충분.

## 6. 보안

- **모든 로그 엔드포인트는 관리자 인증 필수.** `SecurityUrls.ADMIN_PATHS`에 아래 경로 추가:
  `/admin/logs`, `/api/admin/logs/query`, `/api/admin/logs/errors`, `/api/admin/logs/files`,
  `/api/admin/logs/gz-query`, `/api/admin/logs/download`, `/api/admin/logs/download-file`, `/api/admin/logs/stream`.
- **Path traversal 방어** — `download-file` / `gz-query`의 `fileName`은
  `listLogFiles()`가 반환한 **화이트리스트에 존재하는 파일명일 때만** 처리. 절대경로/`..` 차단.
  파일은 반드시 로그 디렉터리 하위로만 resolve.
- 기존 SSE 엔드포인트 통합 시 `SECURED_API_URLS`의 `/api/app/debug/log-stream` 정리.

## 7. 엣지케이스 / 에러 처리

| 상황 | 처리 |
|---|---|
| 로그 파일 없음 (로컬 dev 등) | 빈 결과 + "로그 파일을 찾을 수 없습니다" 안내. 500 미발생 |
| 파일 대용량(100MB) | `RandomAccessFile` 역방향 tail로 끝 N줄만. 전체 로드 안 함 |
| 시간범위에 로그 0건 | 빈 파일 대신 "범위 내 로그 없음" 헤더 한 줄 포함 다운로드 |
| 비정상 입력(줄수/키워드) | 줄수 상한 캡, 키워드 길이 제한 |
| 실시간 구독자 한도 | 기존 Broadcaster max 10명 로직 유지 |
| 다운로드 파일명 | `romrom-log_{range}_{서버현재시각}.log` — 시각은 서버 생성 |
| `.gz` 깨진 파일 | 해제 실패 시 해당 파일만 에러 메시지, 화면 전체는 동작 |

## 8. 문서화 (프로젝트 컨벤션)

- API 동작 추가이므로 해당 `*ControllerDocs.java`(`AdminApiController`용 Docs가 있으면 거기, 없으면 신설/기존 패턴 따름)에
  `@ApiChangeLog`를 배열 최상단에 추가하고 `@Operation` description 작성.
- 기존 `DebugControllerDocs` 통합/정리 반영.

## 9. 작업 순서(요약)

1. `LogLineParser` + `LogFileService` (파일 읽기/집계/목록/gz/시간범위/다운로드)
2. `AdminRequest`/`AdminResponse` 필드 추가
3. `AdminApiController` 엔드포인트 7종 + SSE 통합
4. `SecurityUrls` 경로 등록
5. `AdminPageController` + `layout.html` 메뉴 + `admin/logs.html`
6. 브라우저 부하 최소화 JS (DOM 캡 / visibilitychange / rAF 배칭)
7. Docs 어노테이션 / ApiChangeLog
8. 기존 `DebugController` SSE 통합 정리
