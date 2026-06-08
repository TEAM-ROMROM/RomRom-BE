# 관리자 화면 동접자(온라인 사용자) 집계 — 설계서

- 작성일: 2026-06-08
- 대상: RomRom-BE
- 이슈: #793 (https://github.com/TEAM-ROMROM/RomRom-BE/issues/793)
- 상태: 설계 확정 (구현 대기)

## 1. 배경 및 목표

관리자 화면에서 "지금 서비스에 접속해 활동 중인 사용자 수(동접자)"를 보고 싶다.

모바일 앱(RomRom)은 HTTP 요청이 stateless이므로 "지금 이 순간 연결되어 있다"는 개념이
서버에 존재하지 않는다. 따라서 production 표준 방식인 **Last-Seen + 시간 윈도우 근사**를
사용한다: "최근 N분 안에 활동한 사용자 = 온라인"으로 간주한다.

추가로, 채팅 도메인은 이미 WebSocket 연결 기반으로 "현재 채팅방 입장 상태"를 추적하고
있다(`ChatUserState.leftAt == null`). 이 데이터는 집계 쿼리만 추가하면 거의 비용 없이
"채팅 온라인 수"를 얻을 수 있다.

두 지표는 의미가 다르며 둘 다 제공한다:

| 지표 | 정의 | 데이터 소스 |
|---|---|---|
| **A. 앱 전체 동접** | 최근 5분 내 인증 API를 호출한 고유 회원 수 | Redis Sorted Set (신규) |
| **B. 채팅 온라인** | 현재 채팅방에 입장 중(`leftAt=null`)인 고유 회원 수 | MongoDB `ChatUserState` (기존) |

관리자 화면 표시 예: **"현재 접속 123명 / 그중 채팅 중 18명"**

## 2. 확정된 결정 사항

| 항목 | 결정 |
|---|---|
| 측정 지표 | A + B 둘 다 |
| 온라인 판정 윈도우 | 5분 |
| 관리자 표시 | 현재 숫자만 (시계열/목록 없음) |
| Redis 만료 멤버 청소 | 조회 시 `ZREMRANGEBYSCORE` (별도 스케줄러 없음) |
| 브랜치 전략 | 신규 worktree (기능 개선) |

## 3. 컴포넌트 설계

### 3.1 A — 앱 전체 동접 (Redis Sorted Set)

**자료구조**: Sorted Set 1개
- key: `online:members`
- member: `memberId` (UUID 문자열)
- score: 마지막 활동 시각 (epoch millis)

**기록 (Write) — heartbeat**
- 위치: `RomRom-Domain-Auth` 의 `TokenAuthenticationFilter.doFilterInternal()`
  - 인증 성공이 확정된 지점, 즉 `SecurityContextHolder.getContext().setAuthentication(authentication)`
    직후 ~ `filterChain.doFilter()` 직전. (현재 코드 86라인 부근)
  - 이 시점엔 이미 `CustomUserDetails`(memberId)가 확보되어 있다.
- 동작: `ZADD online:members <현재 epoch millis> <memberId>`
  - score를 매번 덮어쓰므로 항상 "마지막 활동 시각"이 최신으로 유지된다.
- 모든 인증 API 요청이 자동으로 heartbeat가 된다. 별도 ping API·프론트 수정 불필요.
- **실패 격리**: heartbeat 기록 실패(Redis 장애 등)가 본 요청 처리를 막아서는 안 된다.
  try-catch로 감싸고 실패는 로깅만 한다. 인증/응답 흐름에 영향 없음.

**조회 (Read)**
- threshold = now - 5분 (epoch millis)
- 청소: `ZREMRANGEBYSCORE online:members 0 <threshold>` (조회 시마다 먼저 실행)
- 카운트: `ZCARD online:members` (청소 직후이므로 남은 전체가 곧 동접자)
  - 또는 청소 없이 `ZCOUNT online:members <threshold> +inf` 로도 동일 결과.
    청소를 먼저 하므로 `ZCARD`로 단순화한다.

**멀티 인스턴스 안전성**
- Redis는 외부 공유 서버(`RedisConnectionFactory`). 블루그린 배포로 인스턴스가 2개여도
  모든 인스턴스가 같은 Sorted Set에 기록하므로 동접 수가 정확히 합산된다.

**구현 위치 (모듈 규칙 준수)**
- `OnlinePresenceService` 신규: `RomRom-Common` (`com.romrom.common.service`)
  - Redis 접근 로직(`recordHeartbeat`, `countOnlineMembers`) 보유.
  - 이유: heartbeat를 기록하는 `TokenAuthenticationFilter`(Auth 모듈)와
    조회하는 Admin Service(Application 모듈) 양쪽에서 써야 하므로 공통 모듈에 둔다.
- 상수: 윈도우 5분, key 이름은 의미가 드러나는 이름의 상수로 둔다
  (예: `ONLINE_PRESENCE_KEY`, `ONLINE_WINDOW_MILLIS`).

### 3.2 B — 채팅 온라인 (기존 데이터 집계)

- `ChatUserStateRepository` (MongoDB)에 distinct 회원 수 카운트 추가.
  - 메서드: 현재 입장 중(`leftAt == null`)인 **고유 memberId 수**를 센다.
  - 한 회원이 여러 채팅방에 입장 중일 수 있으므로 distinct 필수.
  - MongoDB이므로 단순 `countByLeftAtIsNull`은 방 수를 세게 되어 부정확.
    distinct memberId 집계가 필요하다 (Aggregation 또는 distinct 후 size).
- 집계 메서드는 `ChatRoomService`(또는 별도 조회 서비스)에 둔다.

### 3.3 관리자 조회 API

Admin API 컨벤션을 따른다:
- 위치: `RomRom-Application` — `AdminDashboardService` 신규
  (이미 적절한 대시보드/통계 Admin Service가 있으면 거기에 추가)
- 엔드포인트: `POST /api/admin/dashboard/online-stats`
  - `multipart/form-data` + `@ModelAttribute AdminRequest`
  - action 파라미터 금지, 기능별 별도 URL 원칙 준수
- 응답: `AdminResponse`에 필드 추가
  - `onlineMemberCount` (A: 앱 전체 동접)
  - `chatOnlineMemberCount` (B: 채팅 온라인)
  - `success`/`message` 필드 없음 — HTTP 상태로 성공/실패 판단
- 도메인별 별도 DTO 금지: `AdminRequest`/`AdminResponse`에만 추가
- Service는 `OnlinePresenceService.countOnlineMembers()`(A)와
  채팅 집계 메서드(B)를 각각 호출해 응답에 담는다.

### 3.4 API 문서화 (필수)

- `AdminApiControllerDocs` (또는 해당 Controller의 Docs 인터페이스)에:
  - `@ApiChangeLogs` 배열 **최상단에** 새 `@ApiChangeLog` 추가
    - date: 2026.06.08, author: 해당 Author enum, issueNumber: 793,
      description: "관리자 대시보드 동접자(앱 전체/채팅 온라인) 조회 API 추가"
  - `@Operation` description에 두 지표의 의미와 5분 윈도우 근사 방식 명시.

## 4. 데이터 흐름

```
[사용자 앱]
   │  인증 API 요청 (Bearer 토큰)
   ▼
[TokenAuthenticationFilter]
   │  인증 성공 직후 → OnlinePresenceService.recordHeartbeat(memberId)
   │                     (Redis ZADD, 실패해도 요청은 계속)
   ▼
[Redis Sorted Set: online:members]  ← 모든 인스턴스가 공유

[관리자]
   │  POST /api/admin/dashboard/online-stats
   ▼
[AdminDashboardService]
   ├─ OnlinePresenceService.countOnlineMembers()
   │     → ZREMRANGEBYSCORE (5분 초과 청소) → ZCARD  ⇒ A
   └─ ChatRoomService.countChatOnlineMembers()
         → MongoDB distinct memberId where leftAt is null  ⇒ B
   ▼
[AdminResponse { onlineMemberCount, chatOnlineMemberCount }]
```

## 5. 에러 처리

- **heartbeat 기록 실패**: try-catch로 격리, 로깅만. 본 API 요청 흐름에 영향 없음.
- **Redis 조회 실패**: 관리자 조회 API에서 예외 → `@ControllerAdvice` + `CustomException`
  표준 처리. (A만 실패하고 B는 성공할 수도 있으나, 단순화를 위해 우선 통째로 예외 처리.
  필요 시 후속 개선에서 부분 실패 허용.)
- **인증 안 된 요청**: 화이트리스트 경로/비인증 요청은 memberId가 없으므로 heartbeat
  기록 대상이 아니다. 인증 성공 분기 안에서만 기록하므로 자연히 제외된다.

## 6. 테스트 범위

- `OnlinePresenceService`
  - heartbeat 기록 후 `countOnlineMembers`가 1 증가
  - 5분 초과한 멤버는 조회 시 청소되어 카운트에서 제외
  - 같은 멤버가 여러 번 heartbeat → 카운트 1 (Set 특성)
- 채팅 온라인 집계
  - 한 회원이 여러 방 입장 중 → distinct 카운트 1
  - `leftAt`이 채워진(퇴장) 상태는 카운트 제외
- Admin API 통합
  - 응답에 두 필드가 정상 채워짐, HTTP 200

## 7. 범위 밖 (YAGNI)

- 시계열 그래프 / 시간대별 추이 (현재 숫자만 결정)
- 접속자 memberId/닉네임 목록 (숫자만)
- 별도 heartbeat ping API (기존 인증 필터 재사용으로 불필요)
- 채팅 외 도메인의 WebSocket presence (앱 전체 동접은 API heartbeat로 충분)
- 외부 분석툴(GA/Firebase) 연동

## 8. 구현 순서(요약)

1. GitHub 이슈 생성 (`/suh-issue`) — 이슈 번호 확보
2. 신규 worktree 생성 (`/init-worktree`)
3. `OnlinePresenceService` (Common) — Redis ZADD/ZREMRANGEBYSCORE/ZCARD
4. `TokenAuthenticationFilter`에 heartbeat 호출 추가 (try-catch 격리)
5. `ChatUserStateRepository` distinct 카운트 + `ChatRoomService` 집계 메서드
6. `AdminResponse` 필드 추가 + `AdminDashboardService` + Controller 엔드포인트
7. `AdminApiControllerDocs` `@ApiChangeLog` + `@Operation` 갱신
8. 테스트 작성/실행
