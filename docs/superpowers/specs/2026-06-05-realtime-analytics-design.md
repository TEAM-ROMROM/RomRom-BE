# 접속·방문자 실시간 분석 설계 (#772)

> 부모 Epic: #707 / 연계: #714 (대시보드) / 보안: #672 (MongoDB 보안)
> 작성일: 2026-06-05 / 백엔드: SUH SAECHAN

## 1. 배경 / 문제

현재 어디에도 사용자 행동·접속 이벤트가 기록되지 않아, admin 대시보드에서 "현재 온라인 / 오늘 방문자 / 시간별 동시접속자 추이" 같은 트래픽 지표를 볼 수 없다.

가장 큰 위험은 **지표를 그때그때 별도 테이블로 추가하는 방식**이다. 새 지표(물품 조회수, 검색 키워드 빈도 등)마다 새 컬렉션/스키마/마이그레이션이 생기고, 운영 중 스키마 변경은 사고 위험이 크다.

## 2. 핵심 설계 원칙

**지표별 테이블이 아니라 "공통 이벤트 스트림" 하나로 통합한다.**
모든 사용자 행동을 단일 이벤트 스키마(`UserActivityEvent`)로 쌓고, 모든 지표는 이 스트림에서 집계로 파생한다.
새 지표 추가 = `EventType` enum 값 1개 + 수집 지점에서 recorder 호출 1줄. **DB 마이그레이션 0건.**

## 3. 운영 환경 제약 (이슈 원안 대비 변경점)

이슈는 MongoDB Time Series Collection(5.0+)을 전제했으나, **운영 MongoDB는 시놀로지의 4.4.29** 라 Time Series를 쓸 수 없다. 시놀로지 5.0+ 업그레이드도 불가 방침.

| 이슈 원안 | 본 설계 (확정) |
|---|---|
| Time Series Collection | **일반 `@Document` 컬렉션 + `occurredAt` TTL 인덱스** (기존 `ChatMessage` 패턴 동일) |
| 온라인 = 채팅 WebSocket connect/disconnect | **온라인 = API 요청 기반 Redis TTL set** (실제 앱 접속 반영, 채팅 소켓은 채팅방 진입자만이라 과소집계) |
| 적재 비동기 (@Async/이벤트/큐) | **@Async 전용 스레드풀** (유실 허용, 본 요청 무영향) |

> 디스크 11TB 중 2% 사용이라 Time Series 압축 이점은 당장 불필요. 저장 방식을 추상화해 두어, 추후 Mongo 5.0+ 전환 시 Time Series로 마이그레이션 가능.

## 4. 컴포넌트 설계 (모듈별)

### 4.1 RomRom-Common — 공통 수집 인프라

- **`entity/mongo/UserActivityEvent.java`** (`extends BaseMongoEntity`, `@Document`)
  - 공통 필드: `eventId`(@Id, UUID), `eventType`(EventType), `eventCategory`(EventCategory), `memberId`(UUID?), `anonymousId`(String?), `sessionId`(String?), `path`, `ip`, `userAgent`, `platform`, `occurredAt`(Instant)
  - 확장 필드: `properties`(Map<String, Object>)
  - 인덱스: `@CompoundIndex(def = "{'eventType': 1, 'occurredAt': -1}")`, `@CompoundIndex(def = "{'eventCategory': 1, 'occurredAt': -1}")`
  - **TTL**: `occurredAt`에 TTL 인덱스(예: 90일). 일반 컬렉션이므로 `@Indexed(expireAfterSeconds=...)` 사용
- **`entity/mongo/EventType.java`** (enum) — `PAGE_VIEW`, `API_CALL`, `SESSION_START`, `SESSION_END`, `CONCURRENT_USERS`(스냅샷), 확장: `ITEM_VIEW`, `SEARCH`, `CLICK` ...
  - 각 값은 소속 `EventCategory`를 가짐
- **`entity/mongo/EventCategory.java`** (enum) — `TRAFFIC`, `ENGAGEMENT`, `TRANSACTION`, `SYSTEM`
- **`repository/UserActivityEventRepository.java`** — `MongoRepository`
- **`service/ActivityEventRecorder.java`** — **단일 기록 진입점**. `record(EventType, ...)` 메서드. `@Async("activityEventExecutor")`로 비동기 적재. 내부 try/catch로 적재 실패가 본 요청에 전파되지 않게 한다.
- **`service/OnlineUserTracker.java`** — Redis online set 관리. `touch(identifier)` = `online:{id}` 키 TTL 5분 갱신. `countOnline()` = 활성 키 수. (SCAN 또는 ZSET score=만료시각 방식 중 ZSET 권장: `ZADD online <expireEpoch> <id>`, count는 `ZCOUNT online now +inf`, 정리는 `ZREMRANGEBYSCORE`)
- **`config/ActivityAsyncConfig.java`** — `activityEventExecutor` 전용 `ThreadPoolTaskExecutor` 빈 (적재 유실 허용, 큐 가득 시 CallerRunsPolicy 대신 DiscardPolicy로 본 요청 보호)

### 4.2 RomRom-Web — 수집 지점 연결

- **`interceptor/ActivityTrackingInterceptor.java`** (`HandlerInterceptor`)
  - `afterCompletion`(또는 preHandle)에서 요청 정보 추출 → `ActivityEventRecorder.record(PAGE_VIEW or API_CALL, ...)` + `OnlineUserTracker.touch(...)`
  - 식별: 로그인=memberId(SecurityContext), 비로그인=anonymousId(쿠키, 없으면 발급)
  - **화이트리스트**: 정적 리소스(`/css`,`/js`,`/assets`,`/plugins`), 헬스체크(`/actuator`,`/health`), admin 자체 API(`/api/admin/**` 자기호출), swagger 제외
- **`config/WebConfig.java`** (기존 수정) — `addInterceptors`로 위 인터셉터 등록 + 화이트리스트 `excludePathPatterns`

### 4.3 RomRom-Application — 집계 / API / 스케줄러

- **`scheduler/ConcurrentUserSnapshotScheduler.java`** — `@Scheduled` N분(예: 5분) 주기. `OnlineUserTracker.countOnline()` 결과를 `CONCURRENT_USERS` 이벤트로 적재 → 시간별 동접 추이 데이터화
- **`service/AdminAnalyticsService.java`**
  - `getRealtime()` — Redis online count + 오늘자 Mongo 집계(UV=distinct member/anonymous, PV=PAGE_VIEW count) + 오늘 신규가입/신규거래(기존 도메인 repo)
  - `getTimeseries(eventType|eventCategory, from, to, bucket)` — Mongo aggregation. bucket=HOUR/DAY로 `occurredAt` 그룹핑, count 반환. **범용** — 새 지표도 파라미터만 바꿔 재사용
- **`AdminApiController`** (기존 수정) — 2개 엔드포인트 추가 (POST + multipart + `@ModelAttribute`)
  - `POST /api/admin/analytics/realtime`
  - `POST /api/admin/analytics/timeseries`
- **`AdminRequest`** — `eventType`, `eventCategory`, `bucketUnit`(HOUR/DAY), 기간(`startDate`/`endDate` 기존 재사용 가능) 필드 추가
- **`AdminResponse`** — 내부 static DTO `AdminAnalyticsRealtime`(onlineCount, todayUv, todayPv, todayNewMembers, todayNewTrades), `List<AdminAnalyticsTimeBucket>`(bucketTime, count) 필드 추가. **별도 DTO 클래스 금지 컨벤션** 준수
- **`AdminAnalyticsControllerDocs`** 또는 기존 Docs에 `@Operation` + `@ApiChangeLog` 최상단 추가

### 4.4 Frontend — Admin dashboard 통합 (Thymeleaf + ApexCharts)

- **`layout.html`** — ApexCharts CDN 추가
- **`dashboard.html`** (기존 수정, 별도 페이지 분리 X — 대시보드에 통합해 스크롤로 보도록)
  - 상단: 실시간 카드 4종 — 🟢현재 온라인 / 오늘 방문자(UV) / 신규가입 / 신규거래
  - 하단: 시간별 동시접속자 추이 차트 + 일별 방문자 추이 차트 (기간 필터 연동)
  - 현재 온라인 폴링 갱신 (setInterval, 예: 30초)

## 5. 데이터 흐름

```
모든 HTTP 요청
  └─ ActivityTrackingInterceptor (화이트리스트 통과 시)
       ├─ ActivityEventRecorder.record(...)  [@Async] → Mongo UserActivityEvent
       └─ OnlineUserTracker.touch(...)               → Redis online ZSET (TTL 5분)

스케줄러(5분) → countOnline() → CONCURRENT_USERS 이벤트 적재 (추이 데이터)

Admin API
  ├─ /analytics/realtime    → Redis online count + 오늘자 Mongo/도메인 집계
  └─ /analytics/timeseries  → Mongo aggregation (eventType/기간/버킷 그룹핑)
```

## 6. 에러 처리 / 운영 안정성

- 적재는 `@Async` + 내부 try/catch → 실패해도 본 요청 무영향, 로그만 남김
- Executor 큐 포화 시 DiscardPolicy → 본 요청 지연 방지(이벤트 유실 허용)
- Redis online은 ZSET score=만료epoch → TTL 키 폭증 없이 정확한 count, 만료분은 count 시 score 범위로 자동 제외 + 스케줄러가 주기적 `ZREMRANGEBYSCORE`로 정리
- TTL 인덱스로 raw 이벤트 90일 자동 만료 → 무한 증가 방지

## 7. 확장성 검증 (이슈 핵심 목표)

새 `eventType` 1개 추가 시:
1. `EventType` enum에 값 + category 추가
2. 수집 지점에서 `recorder.record(NEW_TYPE, properties)` 호출 1줄
3. → `timeseries` API에 `eventType=NEW_TYPE` 넘기면 즉시 집계. **DB 마이그레이션 없음.**

## 8. 컨벤션 준수 체크

- Admin: 단일 `AdminRequest`/`AdminResponse`, POST+multipart+@ModelAttribute, action 파라미터 금지(URL 분리), success/message 필드 없음(HTTP 상태로 판단)
- Admin Service는 RomRom-Application에 위치
- Common Entity는 `common/entity/mongo/`, Repository는 `common/repository/` (서브패키지 금지)
- Docs 인터페이스 + `@ApiChangeLog` 최상단 동시 수정
- Mongo 적재라 Flyway 마이그레이션 불필요 (PostgreSQL 변경 없음)

## 9. 범위 (이번 PR)

풀스택 전체 — 백엔드 수집 인프라 + 집계 API + Admin dashboard ApexCharts 프론트.

### Out of scope (후속)
- MongoDB 5.0+ 업그레이드 및 Time Series 전환 (별도 이슈 권장)
- 물품 조회/검색/클릭 등 도메인 이벤트 수집 (인프라만 깔고, 실제 호출은 후속)
- SSE 기반 실시간 푸시 (이번엔 폴링)
