# 회원 360 뷰 + 액션 통합 설계

- **이슈**: [#708 회원 360 뷰 + 액션 통합](https://github.com/TEAM-ROMROM/RomRom-BE/issues/708)
- **부모 Epic**: [#707 관리자 페이지 전면 개편](https://github.com/TEAM-ROMROM/RomRom-BE/issues/707)
- **작성일**: 2026-05-21
- **작성자**: SUH SAECHAN
- **브랜치/Worktree**: `20260521_#707_관리자_페이지_전면_개편_Epic`

---

## 1. 배경 / 문제 정의

현행 `AdminMemberService.getMemberDetailForAdmin`은 회원 기본정보 + 소유 물품 + 신고당한 기록 + 신고건수만 제공한다. 운영자가 한 회원의 운영 상황을 파악하려면 도메인을 여러 번 옮겨 다녀야 하고, 본인이 신고한 이력, 거래 이력, 채팅 이력, 알림 동의/발송 이력, 로그인 이력, AI 사용 이력 등은 아예 노출되지 않는다. 결과적으로 관리자 페이지는 "조회판" 수준에 머물러 있다.

본 설계는 회원 상세를 **12장 카드형 360 뷰**로 재설계하고, 신규 이력 도메인(LoginHistory, AiUsageHistory)을 도입해 관리자 페이지의 운영 가시성을 한 번에 끌어올린다.

---

## 2. 목표

1. 회원 한 명의 모든 활동/상태를 단일 응답(`/api/admin/members/detail`)에서 카드형으로 조회한다.
2. 각 카드의 상세는 별도 sub-endpoint(페이지네이션)로 더 깊이 들여다본다.
3. 신규 이력 도메인(LoginHistory, AiUsageHistory)을 MongoDB로 도입해 어뷰징·운영 분석 기반을 마련한다.
4. 회원 관리 액션(정지/해제/강제탈퇴/물품일괄삭제/알림발송)을 회원 상세 화면 내에서 일괄 수행 가능하게 한다.

---

## 3. 비목표 (이번 Issue 범위 밖)

- 신고 처리 원스톱 워크플로우 (#709)
- 거래 관리 (#710)
- 채팅 관리 (#711)
- 물품 상세 관리 (#712)
- 회원 검색 강화 / 일괄 작업 (#713)
- 대시보드 고도화 (#714)
- 기존 admin 관련 버그 (#715)

위 항목은 회원 360 뷰에서 "deep-link 노출" 정도만 다루고, 실제 기능 구현은 각 이슈에서 진행한다.

---

## 4. 응답 구조 (12장 카드)

```
POST /api/admin/members/detail
→ AdminMemberDetail360Dto (한 응답에 12장 카드 = 카운트 + 통계 + 최근 5건)
```

| # | 카드 | 핵심 데이터 | 데이터 출처 |
|---|------|------------|------------|
| ① | 기본정보 | 닉네임/이메일/가입일/소셜플랫폼/계정상태/위치/현재 정지상태 | Member, MemberLocation, SanctionHistory(active) |
| ② | 소유 물품 | 총/판매중/거래중/완료/삭제 카운트 + 최근 5건 | Item |
| ③ | 거래 | 요청한/받은 거래 카운트, 상태별 카운트, 최근 5건 (상대 deep-link) | TradeRequestHistory |
| ④ | 채팅방 | 총/진행중 카운트 + 최근 5건 (상대 deep-link) | ChatRoom |
| ⑤a | 신고 당함 | 카운트 + 최근 5건 | ItemReport/MemberReport (target 기준) |
| ⑤b | 신고 한것 | 카운트 + 최근 5건 | ItemReport/MemberReport (reporter 기준, 신규) |
| ⑥ | 제재이력 | 총/활성 카운트 + 최근 5건 | SanctionHistory |
| ⑦ | 알림 동의/발송 | Member 동의 필드 + 최근 발송 5건 | Member, NotificationHistory |
| ⑧ | 로그인 이력 | 총/성공/실패 카운트 + 마지막 로그인 + 최근 5건 | LoginHistory (신규) |
| ⑨ | 앱 사용 | lastActiveAt + 가입 후 경과일수 | Member |
| ⑩ | 좋아요 | 카운트 + 최근 5건 | LikeHistory |
| ⑪ | AI 사용 | 타입별 카운트 + 최근 5건 | AiUsageHistory (신규) |
| ⑫ | 거래 통계 | 완료율/취소율/평균 거래기간/최장 미체결 일수 | TradeRequestHistory 집계 |

각 카드 = 카운트/통계 + 최근 5건. **더보기**는 sub-endpoint(페이지네이션).

---

## 5. API 명세

### 5.1 상세 조회 (메인)

| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/api/admin/members/detail` | `memberId` | `AdminResponse.memberDetail360` (12장 카드) |

### 5.2 카드 더보기 sub-endpoints (페이지네이션)

| Path | Body 추가 | Response |
|------|-----------|----------|
| `/api/admin/members/items` | itemStatus/isDeleted 필터 | `Page<Item>` |
| `/api/admin/members/trades` | tradeStatus 필터, side(GIVE/TAKE/BOTH) | `Page<TradeRequestHistory>` |
| `/api/admin/members/chat-rooms` | active 필터 | `Page<ChatRoom>` |
| `/api/admin/members/reports-received` | reportType(ITEM/MEMBER) | `Page<ItemReport \| MemberReport>` |
| `/api/admin/members/reports-filed` | reportType(ITEM/MEMBER) | `Page<ItemReport \| MemberReport>` |
| `/api/admin/members/sanctions` | activeOnly | `Page<SanctionHistory>` |
| `/api/admin/members/notification-history` | type 필터 | `Page<NotificationHistory>` |
| `/api/admin/members/login-history` | loginResult, period | `Page<LoginHistory>` |
| `/api/admin/members/likes` | likeContentType | `Page<LikeHistory>` |
| `/api/admin/members/ai-usage` | aiUsageType, period | `Page<AiUsageHistory>` |

페이지네이션 파라미터: `pageNumber`, `pageSize`, `sortBy`, `sortDirection` (기존 `AdminRequest` 재사용).

### 5.3 액션 endpoints

| Path | Body | 동작 |
|------|------|------|
| `/api/admin/members/suspend` | (현행) memberId, suspendReason, suspendedUntil, reportId, reportType | 정지 |
| `/api/admin/members/unsuspend` | (현행) memberId | 해제 |
| `/api/admin/members/force-withdraw` | memberId, reason | 강제 탈퇴 (전체 cascade) |
| `/api/admin/members/items/bulk-delete` | memberId, itemIds, reason | 보유 물품 일괄 soft delete |
| `/api/admin/members/send-notification` | memberId, title, content, notificationType | admin → 회원 단건 알림 |

모든 API: `POST` + `multipart/form-data` + `@ModelAttribute`. 단일 `AdminRequest` 사용. 응답은 단일 `AdminResponse` 또는 HTTP 상태로 표현.

---

## 6. 신규 도메인 (MongoDB)

### 6.1 LoginHistory

**위치**: `RomRom-Domain-Member/.../entity/mongo/LoginHistory.java`

| 필드 | 타입 | 설명 |
|------|------|------|
| loginHistoryId | String (UUID) | PK |
| memberId | UUID @Indexed | 회원 |
| loginAt | LocalDateTime @Indexed | 로그인 시각 |
| ipAddress | String | IPv4/IPv6 원본 |
| userAgent | String | UA 원본 |
| deviceType | DeviceType (Enum) | MOBILE/WEB/UNKNOWN |
| socialPlatform | SocialPlatform (Enum, nullable) | 소셜 로그인 |
| loginResult | LoginResult (Enum) | SUCCESS/FAIL |
| failReason | String (nullable) | 실패 사유 |

복합 인덱스: `{ memberId: 1, loginAt: -1 }`

**기록 위치**: `AuthService` 로그인 성공/실패 분기 직후. `HttpServletRequest`에서 IP/UA 추출 (Filter or ArgumentResolver). 비동기(`@Async`) 호출로 로그인 응답 지연 방지.

### 6.2 AiUsageHistory

**위치**: `RomRom-Domain-AI/.../entity/mongo/AiUsageHistory.java` (`com.romrom.ai.entity.mongo`)

| 필드 | 타입 | 설명 |
|------|------|------|
| aiUsageHistoryId | String (UUID) | PK |
| memberId | UUID @Indexed (nullable) | 회원 (system 호출은 null) |
| aiUsageType | AiUsageType (Enum) | PRICE_PREDICTION/UGC_FILTER/IMAGE_ANALYSIS |
| requestedAt | LocalDateTime @Indexed | 호출 시각 |
| relatedEntityId | UUID @Indexed (nullable) | itemId 등 연관 객체 |
| requestPayload | Map<String, Object> | 요청 본문 (민감 키 마스킹 후 저장) |
| responsePayload | Map<String, Object> | 응답 본문 (민감 키 마스킹 후 저장) |
| isSuccess | Boolean | 성공 여부 |
| errorMessage | String (nullable) | 실패 시 메시지 |
| durationMs | Long | 소요 시간 |
| modelName | String | 모델명 (gemini-1.5/ollama-llama 등) |

복합 인덱스: `{ memberId: 1, requestedAt: -1 }`, `{ relatedEntityId: 1 }`

**기록 방식**: AOP 기반 `@AiTracked(type = ...)` 어노테이션. `AiUsageTrackingAspect`가 `@Around`로 호출 wrapping → duration/success/error/payload 캡처 → `AiUsageHistoryService.record()` 비동기 호출.

**대상 호출 지점**: VertexAI/Ollama 호출 메서드 (가격예측, UGC 필터, 이미지 분석).

---

## 7. 컴포넌트

### 7.1 Service 계층

**`AdminMemberService` 메서드 추가**
- `getMemberDetail360(memberId)` → `AdminMemberDetail360Dto`
  - 12개 카드 메서드 병렬 호출 (`CompletableFuture.supplyAsync` × 12)
  - 부분 실패 허용 (try-catch로 카드별 격리, 실패 카드는 null + WARN 로그)
- 카드 빌더 메서드 12개: `getBasicInfo`, `getOwnedItemsCard`, `getTradesCard`, `getChatRoomsCard`, `getReportsReceivedCard`, `getReportsFiledCard`, `getSanctionsCard`, `getNotificationConsentCard`, `getLoginHistoryCard`, `getAppUsageCard`, `getLikesCard`, `getAiUsageCard`, `getTradeStatsCard`
- 페이지네이션 메서드 10개: `listOwnedItems`, `listTrades`, `listChatRooms`, `listReportsReceived`, `listReportsFiled`, `listSanctions`, `listNotifications`, `listLoginHistory`, `listLikes`, `listAiUsage`
- 액션 메서드 3개 신규: `forceWithdrawMember`, `bulkDeleteItems`, `sendNotificationToMember`

**`LoginHistoryService`** (신규)
- `record(memberId, ip, ua, deviceType, socialPlatform, result, failReason)` — `@Async` 비동기 저장

**`AiUsageHistoryService`** (신규)
- `record(memberId, type, request, response, success, error, durationMs, modelName)` — 민감 키 마스킹 후 저장. `@Async`.

**`AiUsageTrackingAspect`** (신규)
- `@AiTracked` 어노테이션 메서드를 `@Around`로 가로채 자동 기록

### 7.2 Repository 추가 메서드

| Repository | 메서드 |
|------------|--------|
| `MemberReportRepository` | `findByReporterOrderByCreatedDateDesc(Member, Pageable)`, `countByReporter` |
| `ItemReportRepository` | `findByMemberOrderByCreatedDateDesc(Member, Pageable)`, `countByMember` (없으면 추가) |
| `NotificationHistoryRepository` | `findByMemberIdOrderByPublishedAtDesc(UUID, Pageable)`, `countByMemberId` |
| `TradeRequestHistoryRepository` | `findByGiveItem_Member_MemberIdOrTakeItem_Member_MemberId(UUID, UUID, Pageable)`, 상태별 카운트 |
| `LoginHistoryRepository` (신규) | `findByMemberIdOrderByLoginAtDesc(UUID, Pageable)`, `findFirstByMemberIdAndLoginResultOrderByLoginAtDesc(UUID, LoginResult)`, `countByMemberId`, `countByMemberIdAndLoginResult(UUID, LoginResult)` |
| `AiUsageHistoryRepository` (신규) | `findByMemberIdOrderByRequestedAtDesc(UUID, Pageable)`, `countByMemberIdAndAiUsageType(UUID, AiUsageType)`, `findByRelatedEntityIdOrderByRequestedAtDesc(UUID, Pageable)` |

### 7.3 DTO

- `AdminMemberDetail360Dto` — 12개 카드 sub-DTO 포함
- 각 카드 sub-DTO (`OwnedItemsCard`, `TradesCard`, `ChatRoomsCard`, `ReportsReceivedCard`, `ReportsFiledCard`, `SanctionsCard`, `NotificationConsentCard`, `LoginHistoryCard`, `AppUsageCard`, `LikesCard`, `AiUsageCard`, `TradeStatsCard`)
- `AdminResponse`에 신규 필드 추가:
  - `memberDetail360`, `memberItemsPage`, `memberTradesPage`, `memberChatRoomsPage`, `memberReportsReceivedItem`, `memberReportsReceivedMember`, `memberReportsFiled`, `memberSanctionsPage`, `memberLoginHistoryPage`, `memberLikesPage`, `memberAiUsagePage`, `memberNotificationHistoryPage`

**컨벤션 준수**: 도메인별 별도 DTO 만들지 않음. 모두 단일 `AdminRequest` / `AdminResponse` 사용.

### 7.4 Enum / ErrorCode 추가

- `LoginResult` (SUCCESS, FAIL) — 신규 enum
- `AiUsageType` (PRICE_PREDICTION, UGC_FILTER, IMAGE_ANALYSIS) — 신규 enum
- `DeviceType` — **기존 enum 재사용** (ANDROID, IOS, OTHER)
- `SanctionType` (SUSPEND, UNSUSPEND, FORCE_WITHDRAW, BULK_DELETE_ITEMS) — **신규 enum** (현재는 없음. SanctionHistory.reportType이 String이라 액션 구분 모호 → 명시적 enum 필드 추가)
- `ErrorCode` 추가: `MEMBER_ALREADY_DELETED`, `ADMIN_SELF_ACTION_FORBIDDEN`, `ADMIN_TARGET_FORBIDDEN`

**SanctionHistory 필드 추가** (MongoDB, 마이그레이션 불필요):
- `sanctionType: SanctionType` (신규)
- `executorAdminId: UUID` (신규 — 누가 처리했는지 감사)

**Admin 식별 정정**: `Member.role == Role.ROLE_ADMIN` 으로 판별 (AccountStatus가 아님).

**탈퇴 status 정정**: `AccountStatus.DELETE_ACCOUNT` (DELETED_ACCOUNT 아님 — 기존 값).

### 7.5 강제 탈퇴 cascade 확장

`MemberService.deleteMemberRelatedData`를 `deleteMemberRelatedDataExpanded`로 확장 (또는 강제탈퇴 전용 메서드 신설):

- `memberLocationRepository.deleteByMember*` (현행)
- `memberItemCategoryRepository.deleteByMember*` (현행)
- `itemService.softDeleteAllByMember(memberId)` (신규 cascade)
- `tradeRequestHistoryService.forceCancelByMember(memberId)` (신규 cascade — PENDING/CHATTING 상태만)
- `chatRoomService.forceCloseByMember(memberId)` (신규 cascade)
- `notificationService.sendForceWithdrawNotification(memberId)` (선택)
- `member.setAccountStatus(DELETED_ACCOUNT)` + `member.setEmail(null)` (현행)
- 토큰 무효화 (현행)
- `SanctionHistory`에 `FORCE_WITHDRAW` 기록 + executorAdminId 포함
- `LoginHistory`는 보존 (감사)

---

## 8. 데이터 흐름

### 8.1 360 조회

1. Admin UI → `POST /api/admin/members/detail` (memberId)
2. `AdminApiController.getMemberDetail` → `AdminMemberService.getMemberDetail360`
3. 12개 카드 메서드 병렬 호출 (`CompletableFuture.supplyAsync(..., adminMemberDetailExecutor)`)
4. 각 카드는 `@Transactional(readOnly = true, propagation = REQUIRES_NEW)` 또는 트랜잭션 외부 read-only
5. `CompletableFuture.allOf(...).join()` → DTO 조립
6. `AdminResponse.memberDetail360` 반환

### 8.2 강제 탈퇴

1. Admin UI → `POST /api/admin/members/force-withdraw` (memberId, reason)
2. `AdminMemberService.forceWithdrawMember` (Transactional)
3. 권한 검증: target == self → 거부. target.accountStatus == ADMIN → 거부.
4. `SanctionHistory` FORCE_WITHDRAW 기록 (executorAdminId 포함)
5. `MemberService.deleteMemberRelatedDataExpanded(memberId)` 호출 (확장 cascade)
6. 토큰 무효화
7. HTTP 200 응답

### 8.3 LoginHistory 기록

```
AuthService.loginWith{Kakao,Google,Apple} 성공/실패 분기
  └── LoginHistoryService.record(memberId, ip, ua, deviceType, socialPlatform, SUCCESS/FAIL, reason) @Async
```

IP/UA 추출: 별도 `@CurrentRequestInfo` ArgumentResolver 또는 Filter에서 ThreadLocal 보관.

### 8.4 AiUsageHistory 기록

```
@AiTracked(type = PRICE_PREDICTION)
public PricePrediction predict(Item item) { ... }
   ↓
AiUsageTrackingAspect (Around)
  - 시작 시각 + memberId(SecurityContext) + relatedEntityId(메서드 인자에서 추출 가능 시) 캡처
  - proceed()
  - duration + success + (error|response) 캡처
  - AiUsageHistoryService.record(...) @Async
```

---

## 9. 에러 처리

| 케이스 | HTTP | ErrorCode |
|--------|------|-----------|
| 존재하지 않는 memberId | 404 | `MEMBER_NOT_FOUND` |
| 강제탈퇴 대상이 이미 DELETED_ACCOUNT | 409 | `MEMBER_ALREADY_DELETED` |
| 자기 자신 작업 | 403 | `ADMIN_SELF_ACTION_FORBIDDEN` |
| 다른 admin 대상 작업 | 403 | `ADMIN_TARGET_FORBIDDEN` |
| 12개 카드 중 일부 실패 | 200 + 해당 카드 null + WARN | 부분 실패 허용 |
| bulk 부분 실패 | 200 + 개별 결과 배열 | 컨벤션 |
| Mongo 다운 | 200 + 해당 카드 null + WARN | 가용성 우선 |
| 잘못된 enum | 400 | `INVALID_REQUEST` |
| 빈 memberIds 배열 | 400 | `INVALID_REQUEST` |

전역 처리: `@ControllerAdvice` + `CustomException`. 응답 본문에 `success` / `message` 없음 (HTTP 상태로 표현 — 컨벤션).

---

## 10. 권한 / 보안

**권한**
- `@PreAuthorize("hasRole('ADMIN')")` — 모든 admin endpoint
- 자기 자신 대상 작업 차단: `force-withdraw`, `suspend`, `bulk-delete-items`
- 다른 admin 대상 작업 차단 (accountStatus == ADMIN_ACCOUNT)
- 모든 admin 액션은 `SanctionHistory.executorAdminId` 기록

**보안**
- PII: 응답 평문 노출 OK (admin only), **로그는 마스킹** (Logback 마스킹 필터)
- LoginHistory IP/UA: 원본 저장 + 응답 노출 OK
- AiUsageHistory payload: 민감 키(password/token/refreshToken 등) 마스킹 후 저장 (record 메서드 내부 정규식 + 키 화이트리스트)
- 알림 발송: 제목 100자 / 내용 1000자 길이 제한
- Mass-action rate limit: admin당 분당 30건 (force-withdraw, bulk-*) — 인터셉터 또는 RateLimiter

---

## 11. 성능

- 12 카드 병렬 조회: `CompletableFuture` + 전용 `ThreadPoolTaskExecutor` (`adminMemberDetailExecutor`, core 8 / max 16)
- 카드별 트랜잭션 분리: `REQUIRES_NEW + readOnly` 또는 트랜잭션 외부 read-only
- Item/Trade/Chat: 페이지네이션 5건 + 필요한 필드만 fetch (이미지 1장, 설명 100자 truncate 검토)
- N+1 방지: TradeRequestHistory → giveItem.member, takeItem.member fetch join 또는 EntityGraph
- MongoDB 인덱스: 위 6장 명시
- Postgres 인덱스 누락 시 Flyway 마이그레이션 추가 (멱등성 컨벤션 준수)
- 응답 크기 한계: 카드별 최근 N건 = 5 고정
- 메트릭: 카드 12장 각 실행 시간 측정 → Micrometer
- 목표 p95: < 1.5초

---

## 12. 테스트

**단위**
- `AdminMemberServiceTest`: 360 조회 성공/부분실패, 자기자신 차단, 강제탈퇴 cascade, bulk-delete 부분 실패
- `LoginHistoryServiceTest`: 성공/실패 record, IP/UA 추출
- `AiUsageHistoryServiceTest`: 민감 키 마스킹

**통합**
- `AdminMemberDetail360IntegrationTest`: 12 카드 전부 검증 + 10개 sub-endpoint 페이지네이션
- `AdminMemberActionIntegrationTest`: 강제탈퇴 cascade DB 검증, bulk-delete 부분실패, 알림 발송
- `LoginHistoryRecordIntegrationTest`: 카카오/구글/애플 로그인 성공/실패 LoginHistory 생성 검증
- `AiUsageHistoryRecordIntegrationTest`: `@AiTracked` AOP 검증 (mock Vertex/Ollama)

**성능**
- 12 카드 병렬 조회 p95 < 1.5초 (회원 1명 / 활동량 많은 회원 / 신규 회원)

---

## 13. 마이그레이션

**MongoDB**
- 컬렉션 자동 생성 + 인덱스 명시: `SystemConfigService.onApplicationReady()`에서 `MongoTemplate.indexOps().ensureIndex(...)`로 6개 인덱스 보장

**Postgres**
- 신규 테이블/컬럼 없음
- 인덱스 누락 검토 후 필요 시 `V1_7_x__add_admin_member_360_indexes.sql` 작성 (멱등성: `IF EXISTS`/`DO $$ BEGIN … END $$;` 컨벤션 준수)
- 검토 대상: `trade_request_history`, `chat_room`, `item_report.member_id`, `member_report.reporter_id`

---

## 14. API 문서화

- `AdminMemberControllerDocs` (없으면 신설) — 신규 endpoint 16개 (1 detail + 10 sub-list + 5 action) `@Operation` 추가
- `@ApiChangeLogs` 배열 **최상단**에 `@ApiChangeLog(date="2026.05.21", author=SUH_SAECHAN, issueNumber=708, description="회원 360 뷰 + 액션 통합")` 추가
- description에 12 카드 구조 및 예시 JSON 포함

---

## 15. 커밋 분리 (단일 PR 내)

1. enum/ErrorCode/SanctionType 확장
2. LoginHistory 도메인 + AuthService 기록 인터셉터
3. AiUsageHistory 도메인 + `@AiTracked` AOP + 호출 지점 어노테이션
4. AdminMemberService 360 + sub-list + action 메서드
5. AdminApiController 신규 endpoint + Docs 갱신
6. 통합 테스트
7. (선택) Postgres 인덱스 마이그레이션 + MongoDB 인덱스 초기화 로직

커밋 메시지: `회원 360 뷰 + 액션 통합 : feat : {상세} https://github.com/TEAM-ROMROM/RomRom-BE/issues/708`

---

## 16. 영향 범위 / 비호환 변경

- 기존 응답 필드 `memberDetail` (AdminMemberDetailDto) → 본 이슈는 신규 필드 `memberDetail360` (AdminMemberDetail360Dto)로 분리. 기존 필드는 한 버전간 deprecated 표기 후 다음 버전에서 제거. FE 측 대응 필요.
- 강제 탈퇴 cascade가 Item/Trade/Chat 도메인까지 확장됨. 기존 `MemberService.deleteMemberRelatedData` 호출 경로(자발적 탈퇴)는 동작 보존 — 확장은 별도 메서드(`deleteMemberRelatedDataExpanded`)로 분리하여 자발적 탈퇴 흐름에는 영향 없음.
- `@AiTracked` 추가는 호출 지점의 동작/응답에 영향 없음 (Around에서 try-finally로 보호).

---

## 17. TODO (후속 작업)

### 테스트 (이번 PR에서 보류 — 별도 후속 issue로 분리)

- [ ] `AdminMemberServiceTest` — 360 조회 성공/부분실패, 자기자신 차단, 강제탈퇴 cascade, bulk-delete 부분 실패
- [ ] `LoginHistoryServiceTest` — 성공/실패 record, IP/UA 추출
- [ ] `AiUsageHistoryServiceTest` — 민감 키 마스킹, 50KB truncate
- [ ] `AdminMemberDetail360IntegrationTest` — 12 카드 전부 검증 + 11개 sub-endpoint 페이지네이션
- [ ] `AdminMemberActionIntegrationTest` — 강제탈퇴 cascade DB 검증, bulk-delete 부분실패, 알림 발송
- [ ] `LoginHistoryRecordIntegrationTest` — 카카오/구글/애플 로그인 성공/실패 LoginHistory 생성 검증
- [ ] `AiUsageHistoryRecordIntegrationTest` — `@AiTracked` AOP 검증 (mock Vertex/Ollama)
- [ ] 성능: 12 카드 병렬 조회 p95 < 1.5초 검증

### 도메인 후속 작업 (구현 중 발견)

- [ ] ItemStatus enum 확장: `AVAILABLE/EXCHANGED` → `FOR_SALE/RESERVED/SOLD_OUT/DELETED` 매핑 (OwnedItemsCard.reservedCount 정상화)
- [ ] ChatRoom 엔티티에 `closedAt` 필드 추가 → 강제탈퇴 시 chat room close 로직 활성화
- [ ] TradeStatsCard.avgTradeDays / longestPendingDays 집계 쿼리 구현 (현재 null/0 고정)
- [ ] `LoginHistoryRepository.findByMemberIdAndLoginResultOrderByLoginAtDesc(UUID, LoginResult, Pageable)` 추가 (현재 listLoginHistory는 in-memory 필터)
- [ ] NotificationType에 `ADMIN_NOTIFICATION` enum 값 추가 (현재 ANNOUNCEMENT로 디폴트 처리)
- [ ] SanctionHistory에 `affectedItemIds` 필드 추가 (bulkDeleteItems 영향 itemId 명시 기록)
- [ ] UGC 필터에 `@AiTracked` 적용: `@AiTracked` 어노테이션을 `RomRom-Common`으로 이동하여 순환 의존 회피
- [ ] EmbeddingService(SUH-AIder fallback 경로)에도 `@AiTracked` 적용

## 18. Open Questions / 후속 이슈 후보

- LoginHistory 보관 정책 (TTL 6개월? 1년? 영구?) — 본 이슈에서는 영구 보관, 후속에서 TTL 적용 검토
- AiUsageHistory payload 크기 (대용량 응답 시) → 본 이슈에서는 50KB 초과 시 truncate + flag, 후속 최적화
- 알림 발송 시 대상 회원이 알림 동의 OFF 한 경우 → admin 발송은 무시하고 강제 발송 vs 거부 — **본 이슈에서는 강제 발송**(운영 메시지 성격), 후속에서 정책 분리
