# ChatUserState Mongo→PostgreSQL 이전 설계 (이슈 #764)

## 1. 문제 정의

### 증상 (이슈 #764)
채팅방 목록 조회 시 `hasNext` 값이 서버측 필터링 전 원본 데이터 개수 기준으로 계산되어,
실제 반환 채팅방 수와 불일치한다. 클라이언트가 빈 페이지를 불필요하게 추가 요청한다.

시나리오: 전체 31개 / 마지막 1개 삭제방 / 페이지 크기 30
- 1페이지: 30개 반환, `hasNext=true`
- 2페이지: 0개 반환(삭제방 필터링됨), `hasNext=false`
- 클라이언트가 의미 없는 추가 요청 발생

### 근본 원인
`ChatRoom`은 **PostgreSQL**, `ChatUserState`(삭제/읽음/접속 상태)는 **MongoDB**.
한 채팅방을 표현하는 데이터가 두 DB로 쪼개져 있다.

- 채팅방 목록 페이지네이션은 PostgreSQL `ChatRoom`에서 수행 (`findByTradeReceiverOrTradeSender`)
- 삭제방(soft delete = `ChatUserState.removedAt != null`) 필터링은 fetch 후 **Java 스트림**에서 수행
  (`ChatRoomService.buildChatRoomDetailResponse`, line 304-312)
- `hasNext`는 PostgreSQL slice 기준(`chatRoomsSlice.hasNext()`)이라 필터링 전 데이터로 판단

→ 서로 다른 DB이므로 페이지 쿼리 WHERE에서 삭제방을 거를 수 없어, 필터링이 Java 단으로 밀린 것이 원인.

### 설계 부채 진단
`ChatUserState`는 "채팅방 멤버십 + 삭제상태"를 표현하는 **본질적으로 관계형 데이터**다.
(ChatRoom과 1:N, Member와 N:1, `chatRoomId`로 ChatRoom을 참조하나 FK 없음)
메시지 본문(`ChatMessage`)이 MongoDB라 그 옆에 함께 둔 것이 과한 선택이었다.

**교정 방향**: `ChatMessage`는 Mongo 유지(대량 메시지는 Mongo 적합), `ChatUserState`만 PostgreSQL로 이전한다.
이번 작업은 단순 버그픽스가 아니라 **잘못 배치된 엔티티를 제자리로 옮기는 설계 교정**이다.

## 2. 목표 / 비목표

### 목표
- `ChatUserState`를 MongoDB → PostgreSQL로 이전
- 채팅방 목록 페이지 쿼리에서 삭제방(`removed_at IS NULL`)을 DB 레벨에서 필터링
- `hasNext`·페이지당 반환 개수 둘 다 정확하게 보정
- 기존 MongoDB 데이터를 서버 기동 1회성 러너로 무손실 이전

### 비목표
- `ChatMessage`(MongoDB) 이전 — 유지
- 읽음/접속(`leftAt`/`present`) 기능 동작 변경 — 저장 위치만 PG로, 의미는 동일
- 페이지네이션 UI/응답 스키마 변경 (`isPresent` JSON 필드 등 유지)

## 3. 데이터 모델

### 새 PostgreSQL 엔티티: `ChatUserState`
경로: `RomRom-Domain-Chat/.../entity/postgres/ChatUserState.java`
(기존 `entity/mongo/ChatUserState.java`는 마이그레이션 완료 후 제거)

```
@Entity, @SuperBuilder, @Getter, @Setter, @NoArgsConstructor, @AllArgsConstructor
extends BasePostgresEntity   // createdDate, updatedDate 자동 감사

@Id @GeneratedValue(UUID)  UUID chatUserStateId
@Column(nullable=false)    UUID chatRoomId      // ChatRoom 참조 (아래 JOIN 전략 참고)
@Column(nullable=false)    UUID memberId
LocalDateTime leftAt        // 읽음 커서 (null=접속중)
LocalDateTime removedAt     // null=정상, 값 있으면 나에게만 삭제된 방

// 기존 Mongo 엔티티의 도메인 메서드 그대로 이식:
@JsonProperty("isPresent") boolean isPresent()   // leftAt == null
boolean isDeleted()                              // removedAt != null
void removeRoom()                                // removedAt = now
void enterChatRoom()                             // leftAt = null
void leaveChatRoom()                             // leftAt = now
static ChatUserState create(chatRoomId, memberId) // leftAt=now, removedAt=null
```

- **유니크 제약**: `(chat_room_id, member_id)` — 기존 Mongo `@CompoundIndex(unique=true)` 대응
- `@SuperBuilder` 필수: `BasePostgresEntity`가 `@SuperBuilder`이므로 `@Builder` 사용 불가
- `@JsonProperty("isPresent")` 유지: `getOpponentState`/메시지 조회 응답이 `ChatUserState`를 Entity 직접 반환하므로
  직렬화 형태(`isPresent` 필드)를 깨면 안 됨 (프로젝트 Entity 직접 응답 컨벤션)

### JOIN 전략 (chatRoomId 참조 방식)
페이지 쿼리에서 `ChatRoom`과 `ChatUserState`를 조인해야 한다. 두 안:

- **안 A (권장)**: `ChatUserState.chatRoomId`를 `UUID` 컬럼으로 두고, 페이지 쿼리에서
  `ChatRoom c JOIN ChatUserState s ON s.chatRoomId = c.chatRoomId AND s.memberId = :me WHERE s.removedAt IS NULL`
  형태로 JPQL/명시 조인. 엔티티 간 `@ManyToOne` 매핑 없이 컬럼 값으로 조인 → 기존 Mongo 구조와 최소 차이.
- 안 B: `@ManyToOne ChatRoom` 연관관계 매핑. 더 정석이나 엔티티 양방향 변경 범위 커짐.

→ **안 A 채택**: 기존 코드가 `chatRoomId` UUID로 일관되게 동작하므로 변경 범위 최소.

## 4. Repository 재구현

새 `ChatUserStateRepository extends JpaRepository<ChatUserState, UUID>`
(경로: `repository/postgres/`). 기존 Mongo 레포의 9개 메서드를 **동일 시그니처**로 제공해
호출부 변경을 import 교체 수준으로 최소화한다.

| 메서드 | 구현 |
|---|---|
| `findByChatRoomIdAndMemberId` | 파생 쿼리 그대로 |
| `findByChatRoomIdIn(List)` | 파생 쿼리 |
| `countByChatRoomIdIn(List)` | 파생 쿼리 |
| `findByMemberIdAndLeftAtIsNull` | 파생 쿼리 |
| `findByMemberIdAndChatRoomIdIn` | 파생 쿼리 |
| `deleteAllByChatRoomId` | `@Modifying @Query` 또는 파생 |
| `findByChatRoomIdAndMemberIdNot` | 파생 쿼리 |
| `markRemovedIfNotRemoved` | 아래 별도 — 원자성 보장 |

### `markRemovedIfNotRemoved` 원자성 (race condition 방지 — #748)
기존 Mongo `findAndModify`(조건 `removedAt==null` + set `removedAt=now`, returnNew)를
PostgreSQL 조건부 UPDATE로 대체한다.

```
@Modifying
@Query("UPDATE ChatUserState s SET s.removedAt = :now
        WHERE s.chatRoomId = :chatRoomId AND s.memberId = :memberId AND s.removedAt IS NULL")
int markRemovedIfNotRemoved(chatRoomId, memberId, now)
```

- 반환 int(영향 행 수). `1`이면 이번 호출이 removed 확정, `0`이면 이미 removed (중복 요청).
- 호출부(`ChatRoomService.handleRoomExit`)는 기존 `null 체크`를 `== 0 체크`로 변경하고,
  확정 후 갱신된 엔티티가 필요하면 재조회. (조건부 UPDATE는 단일 쿼리 원자성으로 race 방지)
- 트랜잭션 격리 하에서 `WHERE removedAt IS NULL` 조건부 UPDATE는 동시 두 요청 중 하나만 1행 갱신 →
  Mongo findAndModify와 동일한 원자 보장.

## 5. 페이지네이션 수정

### `ChatRoomRepository.findByTradeReceiverOrTradeSender` 변경
삭제방을 DB 레벨에서 제외하도록 `ChatUserState` 조인 + `removedAt IS NULL` 조건 추가.

```
SELECT c FROM ChatRoom c
  JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender
  JOIN FETCH c.tradeRequestHistory trh JOIN FETCH trh.takeItem JOIN FETCH trh.giveItem
  JOIN ChatUserState s ON s.chatRoomId = c.chatRoomId
WHERE (c.tradeReceiver = :member OR c.tradeSender = :member)
  AND s.memberId = :myMemberId
  AND s.removedAt IS NULL
countQuery = (동일 조인/조건의 count)
```

- 동일하게 `findByMemberAndItemId`(물품별 조회)에도 `removedAt IS NULL` 조인 조건 적용
- 시그니처에 `myMemberId`(UUID) 파라미터 추가 필요 → 호출부(`getRooms`, `getRoomsByItemId`) 인자 전달

### `ChatRoomService.buildChatRoomDetailResponse` 단순화
- 기존 Java 스트림 `filter(count != -1L)` (삭제방 제거) **삭제** — DB에서 이미 걸러짐
- `getUnreadCountsByNPlusOneQuery`의 `if (state.isDeleted()) return -1L;` 분기 제거
  (삭제방은 더 이상 목록에 들어오지 않음)
- `hasNext`는 이제 필터링과 무관하게 PG slice 기준이 정확 → 별도 보정 불필요
- 페이지당 반환 개수도 정확해짐

## 6. 기존 데이터 마이그레이션 (서버 기동 1회성 러너)

새 `@Component ChatUserStateMigrationRunner implements ApplicationRunner`
(또는 `SystemConfigService.onApplicationReady` 내 호출 — 프로젝트 컨벤션 위치)

```
1. SystemConfig에서 마이그레이션 완료 플래그 조회 → 이미 true면 즉시 return (멱등)
2. Mongo ChatUserState 전체 조회 (mongoTemplate / 기존 Mongo 레포 임시 유지)
3. 각 문서를 새 PG ChatUserState로 매핑 (chatRoomId, memberId, leftAt, removedAt)
   - 이미 PG에 (chatRoomId, memberId) 존재하면 skip (재실행 안전)
4. PG saveAll (배치)
5. SystemConfig 완료 플래그 = true 저장
6. 로그: 이전 건수 기록
```

- Mongo `ChatUserState` 엔티티/레포는 **마이그레이션 동안만 임시 유지**.
  운영 검증 완료 후 다음 배포에서 Mongo 엔티티/레포/러너 제거.
- 플래그 키: `SystemConfig`에 `CHAT_USER_STATE_MIGRATED` 등 (기존 SystemConfig 패턴 따름)

### Flyway DDL
경로: `RomRom-Web/src/main/resources/db/migration/V{다음버전}__create_chat_user_state.sql`
CLAUDE.md Flyway 컨벤션 준수 — `DO $$ BEGIN ... EXCEPTION WHEN OTHERS THEN RAISE WARNING ... END $$;` 멱등 블록.

```
- chat_user_state 테이블 생성 (없을 때만):
    chat_user_state_id UUID PK
    chat_room_id UUID NOT NULL
    member_id UUID NOT NULL
    left_at TIMESTAMP
    removed_at TIMESTAMP
    created_date TIMESTAMP NOT NULL
    updated_date TIMESTAMP NOT NULL
    UNIQUE (chat_room_id, member_id)
- 인덱스: (member_id, removed_at), (chat_room_id) — 페이지 조인/조회 최적화
```

## 7. 영향받는 서비스 (호출부 수정)

import 패키지를 `entity.mongo` → `entity.postgres`, `repository.mongo` → `repository.postgres`로 교체.
메서드 시그니처를 동일하게 유지하므로 로직 변경은 최소.

| 파일 | 변경 |
|---|---|
| `ChatRoomService` | import 교체. 페이지 쿼리 호출에 `myMemberId` 인자 추가. `buildChatRoomDetailResponse`/`getUnreadCountsByNPlusOneQuery` 삭제방 분기 제거. `handleRoomExit`의 `markRemovedIfNotRemoved` null체크 → `==0` 체크로 변경 후 재조회 |
| `ChatMessageService` | import 교체. `findByChatRoomIdAndMemberId(Not)` + `isDeleted()`/`isPresent()` 그대로 동작 |
| `ChatTradeCompletionService` | import 교체. `validateOpponentState`의 `isDeleted()` 그대로 |
| `ChatUserStateEnsureService` | import 교체. `findByChatRoomIdIn`/`saveAll`/`create` 그대로 동작 (`DuplicateKeyException` → PG는 `DataIntegrityViolationException` 계열로 변경 검토) |

### Swagger Docs 동기화 (CLAUDE.md 컨벤션)
- `ChatControllerDocs` / `ChatWebSocketControllerDocs`: `isPresent`/`leftAt` 응답 필드 설명 유지 확인.
  저장 위치만 PG로 바뀌고 응답 스키마는 동일하므로 필드 설명 변경 없음. 단,
  hasNext/페이지 동작 변경에 대한 `@ApiChangeLog` 최상단 추가 (date 2026.06.04, issueNumber 764).

## 8. 테스트 전략

- **단위**: 새 PG `ChatUserStateRepository` 파생 쿼리 + `markRemovedIfNotRemoved` 원자성
  (동시 두 호출 중 1행만 갱신되는지 — `@DataJpaTest` 또는 통합)
- **페이지네이션 회귀**: 이슈 #764 시나리오 재현 — 31개 중 마지막 삭제방 →
  1페이지 30개 `hasNext=false` 확인, 2페이지 빈 응답 발생 안 함 검증
- **마이그레이션 러너**: Mongo 데이터 존재 시 PG 이전 건수 일치, 재실행 시 멱등(중복 생성 없음)
- **회귀**: 입퇴장/읽음커서/시스템메시지/거래완료 흐름이 PG 전환 후에도 동일 동작

## 9. 단계별 구현 순서

1. Flyway DDL (`chat_user_state` 테이블)
2. 새 PG `ChatUserState` 엔티티 + `ChatUserStateRepository` (9개 메서드 + `markRemovedIfNotRemoved`)
3. 페이지 쿼리 2개에 `removed_at IS NULL` 조인 조건 추가
4. 4개 서비스 호출부 import/시그니처 교체 + `ChatRoomService` 삭제방 분기 제거
5. 기동 1회성 마이그레이션 러너 + SystemConfig 플래그
6. Swagger Docs `@ApiChangeLog` 추가
7. 테스트 + 회귀 검증
8. (별도 배포) 운영 검증 후 Mongo `ChatUserState` 엔티티/레포/러너 제거

## 10. 리스크 / 롤백

- **리스크**: WebSocket 입퇴장마다 PG UPDATE 발생 (기존 Mongo findAndModify 대체).
  채팅방당 row 2개 수준이라 부하 작음. PG 감당 가능.
- **마이그레이션 미완 시**: 러너 플래그 미설정 → PG 비어 페이지 쿼리 조인 결과 0 → 목록 빈 응답 위험.
  → 러너를 **페이지 쿼리 전환 배포와 동일 시점**에 기동시켜 빈 PG 상태 노출 방지. 검증 후 전환.
- **롤백**: Mongo 엔티티/레포 8단계 전까지 유지하므로, 문제 시 import만 되돌려 Mongo로 복귀 가능.
