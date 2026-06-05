# 채팅방 삭제 비원자성 해결 + 관리자 삭제 관리 설계 (이슈 #750)

## 1. 문제 정의

### 증상 (이슈 #750)
채팅방 완전 삭제(Hard Delete) 시 PostgreSQL과 MongoDB 삭제가 각각 별도 트랜잭션
경계에서 실행되어 비원자적이다. 한쪽만 성공하면 데이터 불일치가 발생한다.

- PG 삭제 성공 → Mongo 삭제 실패: 방은 사라졌으나 메시지·상태가 **고아로 잔존**
- Mongo 삭제 완료 → PG 롤백: 방은 존재하나 **채팅 데이터가 없음**

### 근본 원인
채팅 데이터가 두 DB에 분산되어 있다.
- `ChatRoom` → **PostgreSQL**
- `ChatMessage` → **MongoDB**
- `ChatUserState`(나감/읽음/삭제 상태) → **MongoDB**

물리 삭제는 `ChatRoomService.executeHardDelete()` 한 곳에서 수행된다
(`RomRom-Domain-Chat/.../service/ChatRoomService.java:373-385`):

```java
@Transactional   // ← JPA(PostgreSQL)만 트랜잭션으로 묶임. MongoDB는 경계 밖
private void executeHardDelete(UUID roomId) {
    chatRoomRepository.deleteById(roomId);                  // PostgreSQL
    chatMessageRepository.deleteByChatRoomId(roomId);       // MongoDB (트랜잭션 밖)
    chatUserStateRepository.deleteAllByChatRoomId(roomId);  // MongoDB (트랜잭션 밖)
}
```

호출 경로 2개:
- `handleRoomExit()` — 채팅방 나가기에서 상대도 이미 나간 경우 (`:347`)
- `adminForceDeleteChatRoom()` — 관리자 강제 삭제 (`:366`)

### 현재 삭제 정책 (Soft/Hard 혼합)
- 한쪽만 나감(상대 남아있음): **Soft Delete** — 내 `ChatUserState.removedAt`만 표시
- 양쪽 다 나감(마지막 사람): **Hard Delete** — `executeHardDelete()`로 전부 물리 삭제
- 관리자 강제 삭제: **Hard Delete**

→ #750이 문제 삼는 지점은 위에서 **Hard Delete가 발동하는 두 경우**다.

## 2. 핵심 전략

**동기 흐름에서 Hard Delete를 제거하고,
"Soft Delete(즉시) + 관리자 관리 + 배치 아카이브·물리삭제(비동기)"로 전환한다.**

| 단계 | 시점 | 동작 | DB/IO |
|---|---|---|---|
| 1. Soft Delete | 나가기/관리자삭제 즉시 | `ChatRoom.deletedAt = now()` 표시만 | **PG 단일 트랜잭션** (원자적) |
| 2. 관리 | 운영 중 상시 | 관리자가 삭제 대기 방 목록/상세 조회, 수동 추출, 수동 즉시 삭제 | 조회 위주 |
| 3. 아카이브+청소 | 배치 (30일 경과) | 방 데이터를 `.json.gz`로 backup/에 떨군 뒤 물리 삭제 | 파일 → 그 후 삭제 |

**#750이 사라지는 이유**: PG·Mongo를 한 흐름에서 동시에 지우던 `executeHardDelete()`가
동기 경로에서 빠진다. 나가기/관리자삭제는 PG `deletedAt` 표시만 하므로 100% 원자적.
실제 물리 삭제는 배치가 한 DB씩 처리하고, 실패해도 다음 배치가 재시도하므로
고아 데이터는 일시적이며 결국 수렴한다.

### 설계 결정 요약
- soft-delete 표시: **`ChatRoom.deletedAt` 컬럼 추가** (PG 단일 트랜잭션)
- 유예 기간: **30일** (`deletedAt < now() - 30일`이면 청소 대상)
- 배치: 기존 `OrphanImageCleanupScheduler` 패턴 따름
- 아카이브 포맷: **텍스트 JSON → gzip 압축** (`.json.gz`)
- 아카이브 대상: 채팅 텍스트 로그 + 참여자 정보. **이미지는 URL 참조만**(바이너리 미백업)
- 아카이브 위치: **호스트 마운트 `backup/` 폴더** (무중단 배포 시 파일 유실 방지)
- 관리자 기능: 목록 조회 + 상세 조회 + 추출(export) + 수동 즉시 삭제

## 3. 스키마 변경 (Flyway)

**`ChatRoom` 테이블에 `deleted_at` 컬럼 추가.**
- 경로: `RomRom-Web/src/main/resources/db/migration/V1_4_64__add_chat_room_deleted_at.sql`
  (최신 버전 `V1_4_63` 다음)
- CLAUDE.md Flyway 컨벤션 준수 — `IF EXISTS` 체크 + `DO $$ ... EXCEPTION` 멱등 블록

```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_room')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'chat_room' AND column_name = 'deleted_at') THEN
        ALTER TABLE chat_room ADD COLUMN deleted_at TIMESTAMP;
        -- 배치 청소 대상 조회 최적화 (deleted_at IS NOT NULL 인 방만 스캔)
        CREATE INDEX IF NOT EXISTS idx_chat_room_deleted_at ON chat_room (deleted_at);
    END IF;
EXCEPTION
    WHEN OTHERS THEN RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
```

**엔티티 변경** — `ChatRoom`(`entity/postgres/ChatRoom.java`)에 필드 추가:
```java
private LocalDateTime deletedAt;   // soft delete 시각. null이면 활성, non-null이면 청소 대기

// soft delete 표시 (멱등 — 이미 표시된 방이면 무시)
public void softDelete() {
    if (this.deletedAt == null) {
        this.deletedAt = LocalDateTime.now();
    }
}
```
`BasePostgresEntity`가 `LocalDateTime createdDate/updatedDate`를 쓰므로 타입 일관성 유지.

## 4. ChatRoomService 변경

### (a) `executeHardDelete()` → soft delete로 교체
기존 물리 삭제 호출 지점을 `room.softDelete()` 호출로 교체.
- `handleRoomExit()`의 `:347` (`executeHardDelete(roomId)` → soft delete)
- `adminForceDeleteChatRoom()`의 `:366`
PG·Mongo 동시 삭제 코드(`executeHardDelete` 본문)는 배치 쪽으로 이동/재사용.

### (b) 목록 쿼리 2개에 `deleted_at IS NULL` 필터 추가
사용자 목록에서 soft-delete된 방이 안 보이게 한다.
- `ChatRoomRepository.findByTradeReceiverOrTradeSender` — WHERE에 `AND c.deletedAt IS NULL`
  (countQuery에도 동일 추가)
- `ChatRoomRepository.findByMemberAndItemId` — 동일

### (c) `buildChatRoomDetailResponse` 삭제방 필터링 단순화 검토
현재는 Mongo `ChatUserState` 기반 `-1L` 필터(`:304-310`)로 처리.
PG `deletedAt IS NULL`이 쿼리 단에서 거르므로 해당 분기 단순화 가능 여부 검토
(단, 한쪽만 나간 soft 상태는 여전히 `ChatUserState.removedAt` 기반이므로 혼동 주의).

## 5. 관리자 API

CLAUDE.md Admin 컨벤션 100% 준수: `AdminRequest`/`AdminResponse` 단일 DTO,
기능별 URL 분리, `POST` + `multipart/form-data`(`@ModelAttribute`),
`success`/`message` 필드 없이 HTTP 코드로 판단, Service는 `RomRom-Application`에 위치.

| 엔드포인트 | 동작 | 응답 |
|---|---|---|
| `POST /api/admin/chat-rooms/deleted-list` | soft-delete된 방 목록 (페이지네이션, `deletedAt IS NOT NULL`) | `AdminResponse` (방 목록 + Page 정보) |
| `POST /api/admin/chat-rooms/detail` | 특정 방 전체 메시지 상세 조회 | `AdminResponse` (ChatRoom + ChatMessage 목록) |
| `POST /api/admin/chat-rooms/export` | 방을 `.json.gz`로 추출 다운로드 | 파일 스트림 |
| `POST /api/admin/chat-rooms/force-delete` | 30일 안 기다리고 즉시 아카이브+물리삭제 | `Void` (200 OK) |

- **Controller**: `AdminApiController`에 엔드포인트 4개 추가
- **Service**: `AdminChatRoomService` 신규 → `RomRom-Application/.../service/`
- **`AdminRequest` 추가 필드**: `chatRoomId`, `pageNumber`, `pageSize` (대부분 기존 재사용)
- **`AdminResponse` 추가 필드**: 채팅방 목록/상세용 (Entity 그대로 담기 — 프로젝트 Response 원칙)
- **View**: `AdminPageController`에 관리 페이지 추가 (기존 admin 뷰 패턴 따름)

## 6. 배치 + 아카이브

### `ChatRoomCleanupScheduler` (RomRom-Application/.../scheduler/)
`OrphanImageCleanupScheduler` 패턴 그대로.

```java
@Component @RequiredArgsConstructor @Slf4j
public class ChatRoomCleanupScheduler {
    private static final int ARCHIVE_RETENTION_DAYS = 30;   // 유예 30일

    // @Scheduled(cron = "0 0 4 * * *")  // 매일 새벽 4시 (초기엔 주석, 검증 후 활성화 — 기존 패턴)
    public void cleanupDeletedChatRooms() {
        // 1. deletedAt < now()-30일 인 ChatRoom 조회
        // 2. 각 방: 아카이브 → 물리삭제 (방별 try-catch, 실패는 log.warn 후 다음 방 계속)
    }
}
```

### `ChatRoomArchiveService` (관리자 export와 동일 로직 공유)
1. ChatRoom + ChatMessage(텍스트 + **이미지는 URL 참조만**) + 참여자 정보를 JSON 조립
2. **gzip 압축** → `{호스트마운트}/backup/chat-rooms/{chatRoomId}_{deletedAt}.json.gz`
3. 파일 생성·검증 성공 후에만 물리 삭제 진행
   (아카이브 실패 시 삭제 스킵 → `deletedAt` 유지 → 다음 배치 재시도)

### 물리 삭제 순서 (한 DB씩, 멱등)
```
ChatMessage(Mongo) 삭제 → ChatUserState(Mongo) 삭제 → ChatRoom(PG) 삭제
```
PG를 마지막에 지우므로 중간 실패 시 `deletedAt`이 남아 다음 배치가 재시도.
고아 데이터가 결국 수렴한다.

## 7. 에러 처리 / 멱등성 / 동시성

- **나가기/관리자삭제(soft delete)**: PG 단일 트랜잭션. `markRemovedIfNotRemoved`
  원자성 로직 유지(동시 퇴장 race 방지). 양쪽 나감 확정 시 `deletedAt` 표시 —
  이미 표시된 방이면 `softDelete()`가 멱등 무시.
- **배치 물리 삭제**: 방별 `try-catch`, 실패는 `log.warn` 후 다음 방 계속.
  각 repository 삭제는 멱등(없는 것 지워도 무해).
- **아카이브 실패 = 삭제 중단**: 파일 생성/검증 실패 시 그 방은 물리 삭제 스킵.
  "백업 없이 삭제되는 일"을 원천 차단.
- **수동 즉시 삭제(force-delete)**: 배치와 동일한 아카이브→삭제 로직 재사용.

## 8. 테스트 전략

- **단위**: soft delete 시 `deletedAt` 세팅 + PG 단일 트랜잭션 확인 /
  목록 쿼리가 `deletedAt IS NOT NULL` 방을 제외하는지
- **아카이브**: JSON 조립(이미지 URL 참조만 포함, 바이너리 미포함) +
  gzip 압축/해제 라운드트립 검증
- **배치**: 30일 경과 방만 대상 / 아카이브 실패 시 물리삭제 스킵 /
  한 방 실패해도 나머지 진행
- **회귀**: 한쪽만 나감 → soft(방 유지), 양쪽 나감 → deletedAt 표시(즉시 물리삭제 안 함),
  사용자 목록에서 deletedAt 방 안 보임
- 프로젝트 테스트 컨벤션은 `/suh-spring-test` 패턴 확인 후 맞춤

## 9. Swagger Docs 동기화 (CLAUDE.md 필수 컨벤션)

- **`AdminApiControllerDocs`**: 새 엔드포인트 4개 `@Operation` +
  `@ApiChangeLogs` 최상단에 신규 항목 추가 (date `2026.06.05`, issueNumber `750`)
- **`ChatControllerDocs`**: 채팅방 삭제 동작이 "즉시 물리삭제 → soft delete + 배치 청소"로
  바뀐 점 description 반영 + `@ApiChangeLog` 추가

## 10. 단계별 구현 순서

1. Flyway DDL (`V1_4_64__add_chat_room_deleted_at.sql`) + `ChatRoom.deletedAt` 필드 + `softDelete()`
2. `ChatRoomService`: 물리삭제 호출 → soft delete 교체, 목록 쿼리 2개 `deletedAt IS NULL` 필터
3. `ChatRoomArchiveService`: JSON 조립 + gzip + backup/ 파일 저장 (export·배치 공용)
4. `ChatRoomCleanupScheduler`: 30일 경과 방 아카이브→물리삭제 배치
5. 관리자 API 4종 (`AdminApiController` + `AdminChatRoomService` + `AdminRequest/Response` 필드)
6. 관리자 뷰 페이지 (`AdminPageController`)
7. Swagger Docs `@ApiChangeLog` 추가
8. 테스트 + 회귀 검증

## 11. 리스크 / 롤백

- **리스크**: soft-delete된 방의 데이터가 30일간 물리적으로 남음
  (저장공간/개인정보 보존 기간 고려 — 30일 보관은 분쟁·감사 대응에 부합).
- **호스트 마운트 경로 미설정 시**: backup/ 파일 유실 위험 →
  무중단 배포 환경에서 호스트 볼륨 마운트 필수 (기존 로그 마운트 패턴 참고).
- **배치 스케줄**: 초기엔 `@Scheduled` 주석 처리(수동 검증) →
  운영 검증 후 활성화 (`OrphanImageCleanupScheduler` 동일 패턴).
- **롤백**: `deletedAt` 표시만 하므로 물리 삭제 전이면 컬럼 NULL로 되돌려 복구 가능.
  배치 미활성 상태에선 데이터가 그대로 보존되어 안전.

## 12. #764와의 관계 (참고)

별도 설계 문서 `2026-06-04-chatuserstate-mongo-to-postgres-design.md`(#764)가
`ChatUserState`를 Mongo→PG로 이전한다. 본 #750 설계는 **#764 없이 단독으로 동작**한다.
만약 #764가 먼저 머지되면 `ChatUserState`가 PG로 들어와
물리삭제 시 PG 2개(ChatRoom, ChatUserState) + Mongo 1개(ChatMessage) 구조가 되며,
배치의 물리 삭제 순서는 동일 원칙(Mongo 먼저 → PG 마지막)으로 유지하면 된다.
