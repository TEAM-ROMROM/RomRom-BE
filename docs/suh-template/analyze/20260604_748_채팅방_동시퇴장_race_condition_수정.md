# 채팅방 두 사용자 동시 퇴장 시 hard delete 누락 race condition 수정 — HOW 계획

작성일: 2026-06-04
참조: (plan 문서 없음 — WHAT은 본 문서 0절에 압축 기록)
GitHub 이슈: #748

## 0. WHAT 요약 (plan 대체)

- **문제**: `ChatRoomService.handleRoomExit()`가 "상대 상태 읽기 → isDeleted 판단 → 내 상태 쓰기"의 비원자 check-then-act 패턴. 두 사용자가 거의 동시에 나가면 둘 다 상대 `removedAt==null`을 읽어 각자 soft delete만 수행 → `executeHardDelete()` 미호출 → ChatRoom(Postgres)·ChatMessage(Mongo)·ChatUserState(Mongo) 영구 잔존.
- **재현 빈도**: 낮음. 두 나가기 요청이 ms 단위로 겹쳐 양쪽 read가 상대 write 이전에 끝나야 발동.
- **해결 방식**: B안 — Mongo 단일 문서 원자 연산(`findAndModify`)으로 내 상태를 조건부 removed 표시 + `executeHardDelete()` 멱등화로 중복 호출 안전화.
- **환경**: 서버 단일 인스턴스. → Redis 분산 락(D) 불필요. Mongo 문서 원자성으로 충분.
- **성공 기준**: 두 사용자가 동시에 나가도 ChatRoom·ChatMessage·ChatUserState가 DB에서 완전 삭제된다. 한쪽만 나간 기존 정상 흐름(soft delete + 시스템 메시지)은 그대로 유지.

## 1. 변경 파일 목록

| # | 파일 | 함수/위치 (라인) | 무엇을 | 실행 순서 |
|---|------|----------------|-------|---------|
| 1 | `RomRom-Domain-Chat/.../repository/mongo/ChatUserStateRepositoryCustom.java` | 신규 인터페이스 | `markRemovedIfNotRemoved()` 선언 | 순차(먼저) |
| 2 | `RomRom-Domain-Chat/.../repository/mongo/ChatUserStateRepositoryImpl.java` | 신규 클래스 | MongoTemplate `findAndModify` 원자 구현 | 순차(2번째) |
| 3 | `RomRom-Domain-Chat/.../repository/mongo/ChatUserStateRepository.java` | `extends` 절 (L10) | Custom 인터페이스 상속 추가 | 순차(3번째, Task1 후) |
| 4 | `RomRom-Domain-Chat/.../service/ChatRoomService.java` | `handleRoomExit()` (L325~349) | check-then-act → 원자 update + 재조회 판단 | 순차(마지막, Task1~3 후) |
| 5 | `RomRom-Domain-Chat/.../service/ChatRoomService.java` | `executeHardDelete()` (L370~377) | 멱등 가드 추가(이미 삭제 시 no-op) | [병렬] (Task4와 같은 파일이라 사실상 순차) |

> Task 1·2는 서로 의존(인터페이스→구현)하나 Task 3·4와 분리됨. Task 4·5는 같은 파일이므로 한 번에 편집.

## 2. 태스크별 상세

### Task 1: Custom repository 인터페이스 신규

**파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/mongo/ChatUserStateRepositoryCustom.java` (신규)
**변경 이유**: MongoRepository 파생 메서드로는 원자 `findAndModify`(조건부 update + 갱신본 반환)를 표현할 수 없음. Spring Data MongoDB의 custom fragment 패턴으로 MongoTemplate 접근.

**After** (신규 파일):
```java
package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatUserState;
import java.util.UUID;

public interface ChatUserStateRepositoryCustom {

  /**
   * 아직 removed 표시되지 않은(removedAt == null) 내 상태를 원자적으로 removed 표시한다.
   * 단일 문서 findAndModify 연산이므로 동시 요청에서도 read-modify-write 경합이 발생하지 않는다.
   *
   * @return 갱신된 ChatUserState (이미 removed 상태였으면 조건 불일치로 null)
   */
  ChatUserState markRemovedIfNotRemoved(UUID chatRoomId, UUID memberId);
}
```

**검증**: 컴파일 통과 (인터페이스 단독).

---

### Task 2: Custom repository 구현 (MongoTemplate findAndModify)

**파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/mongo/ChatUserStateRepositoryImpl.java` (신규)
**변경 이유**: Task 1 인터페이스의 실제 원자 연산 구현. 클래스명은 `{RepositoryName}Impl` 규약을 따라야 Spring Data가 자동 연결한다.

**After** (신규 파일):
```java
package com.romrom.chat.repository.mongo;

import com.romrom.chat.entity.mongo.ChatUserState;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatUserStateRepositoryImpl implements ChatUserStateRepositoryCustom {

  private final MongoTemplate mongoTemplate;

  @Override
  public ChatUserState markRemovedIfNotRemoved(UUID chatRoomId, UUID memberId) {
    Query removableStateQuery = new Query(
        Criteria.where("chatRoomId").is(chatRoomId)
            .and("memberId").is(memberId)
            .and("removedAt").is(null)
    );
    Update markRemovedUpdate = new Update().set("removedAt", LocalDateTime.now());
    FindAndModifyOptions returnUpdatedDocument = FindAndModifyOptions.options().returnNew(true);

    return mongoTemplate.findAndModify(
        removableStateQuery, markRemovedUpdate, returnUpdatedDocument, ChatUserState.class);
  }
}
```

**검증**: `mvn -pl RomRom-Domain-Chat -am compile` (외부망 불가 환경이면 사용자 별도 환경에서 빌드).

---

### Task 3: MongoRepository에 Custom 상속 연결

**파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/mongo/ChatUserStateRepository.java`
**함수**: `extends` 절 (line 10)
**변경 이유**: Task 1·2의 custom fragment를 기존 repository에 합쳐 서비스에서 단일 빈으로 주입받게 함.

**Before** (L10):
```java
public interface ChatUserStateRepository extends MongoRepository<ChatUserState, String> {
```

**After** (L10):
```java
public interface ChatUserStateRepository extends MongoRepository<ChatUserState, String>, ChatUserStateRepositoryCustom {
```

**검증**: 컴파일 통과. 기존 메서드 시그니처 변동 없음.

---

### Task 4: handleRoomExit — check-then-act 제거, 원자 update + 재조회 판단

**파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/service/ChatRoomService.java`
**함수**: `handleRoomExit()` (line 325~349)
**변경 이유**: 핵심 수정. "상대 읽기 → 판단 → 내 쓰기" 순서를 "내 상태 원자 removed 표시 → 상대 상태 재조회 → 둘 다 removed면 hard delete"로 교체. 내 write가 먼저 원자 확정되므로, 동시 요청 둘 다 나중에 상대 상태를 조회할 때 최소 한쪽은 상대의 removed를 보게 되어 hard delete가 보장된다(양쪽 다 볼 수도 있으나 Task 5 멱등화로 안전).

**Before** (L325~349):
```java
  private void handleRoomExit(ChatRoom room, UUID myId) {
    UUID roomId = room.getChatRoomId();

    // CHATTING 상태면 거래취소로 변경 (상대방이 더 이상 메시지를 못 보내게 함)
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(room.getTradeRequestHistory().getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
    tradeRequestHistory.changeToCancelIfChatting();

    // 상대방 상태 확인
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(roomId, myId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (opponentState.isDeleted()) {      // 시나리오: 상대방도 이미 나간 경우 -> 진짜 다 지움 (Hard Delete)
      executeHardDelete(roomId);
    } else {                              // 시나리오: 상대방은 아직 남아있는 경우 -> 내 상태만 비표시 (Soft Delete)
      log.debug("상대방이 남아있어 내 상태만 삭제 표시합니다. roomId={}, myId={}", roomId, myId);
      ChatUserState myState = chatUserStateRepository.findByChatRoomIdAndMemberId(roomId, myId)
          .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));
      myState.removeRoom(); // removedAt 설정
      chatUserStateRepository.save(myState);

      // 상대방이 아직 방에 남아있으므로 시스템 메시지 생성 및 전송
      chatMessageService.sendSystemMessage(room, myId, opponentState);
    }
  }
```

**After** (L325~):
```java
  private void handleRoomExit(ChatRoom room, UUID myId) {
    UUID roomId = room.getChatRoomId();

    // CHATTING 상태면 거래취소로 변경 (상대방이 더 이상 메시지를 못 보내게 함)
    TradeRequestHistory tradeRequestHistory = tradeRequestHistoryRepository.findById(room.getTradeRequestHistory().getTradeRequestHistoryId())
        .orElseThrow(() -> new CustomException(ErrorCode.TRADE_REQUEST_NOT_FOUND));
    tradeRequestHistory.changeToCancelIfChatting();

    // 내 상태를 원자적으로 removed 표시 (removedAt == null인 경우에만 갱신).
    // 동시 퇴장 race condition 방지: read-modify-write를 단일 findAndModify로 대체한다.
    ChatUserState myRemovedState = chatUserStateRepository.markRemovedIfNotRemoved(roomId, myId);
    if (myRemovedState == null) {
      // 이미 내가 나간 방을 다시 나가려는 중복 요청 — 추가 처리 불필요
      log.debug("이미 삭제 표시된 채팅방에 대한 중복 나가기 요청입니다. roomId={}, myId={}", roomId, myId);
      return;
    }

    // 내 removed가 원자적으로 확정된 뒤 상대방 상태를 다시 읽는다.
    ChatUserState opponentState = chatUserStateRepository.findByChatRoomIdAndMemberIdNot(roomId, myId)
        .orElseThrow(() -> new CustomException(ErrorCode.CHAT_USER_STATE_NOT_FOUND));

    if (opponentState.isDeleted()) {      // 상대방도 이미 나감 -> 완전 삭제 (executeHardDelete는 멱등)
      executeHardDelete(roomId);
    } else {                              // 상대방은 아직 남아있음 -> 시스템 메시지만 전송 (내 상태는 위에서 이미 removed)
      log.debug("상대방이 남아있어 내 상태만 삭제 표시했습니다. roomId={}, myId={}", roomId, myId);
      chatMessageService.sendSystemMessage(room, myId, opponentState);
    }
  }
```

**변경 핵심 차이**:
- `myState` 조회 + `removeRoom()` + `save()` 3줄 → `markRemovedIfNotRemoved()` 원자 1줄로 대체. 이게 race 원천 차단.
- `myRemovedState == null` 가드 = 중복 나가기 멱등 처리(기존엔 없던 보강).
- soft delete 분기에서 `myState.removeRoom()/save()` 제거됨 — 이미 원자 update에서 수행했으므로. 시스템 메시지 전송만 남김.

**검증**: 아래 5절 동시성 시나리오 테스트.

---

### Task 5: executeHardDelete 멱등 가드

**파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/service/ChatRoomService.java`
**함수**: `executeHardDelete()` (line 370~377)
**변경 이유**: 동시 퇴장 시 양쪽 요청이 모두 "상대도 removed"를 보고 `executeHardDelete()`를 각각 호출할 수 있다. 두 번째 호출이 안전하도록 이미 삭제된 방이면 no-op 처리.

**Before** (L370~377):
```java
  private void executeHardDelete(UUID roomId) {
    log.debug("채팅방에 다른 멤버가 나간 상태이므로, 채팅방을 완전 삭제합니다. roomId={}", roomId);
    chatRoomRepository.deleteById(roomId);
    log.debug("채팅방 메시지 삭제 : roomId={}", roomId);
    chatMessageRepository.deleteByChatRoomId(roomId);
    log.debug("채팅방 멤버 상태 삭제 : roomId={}", roomId);
    chatUserStateRepository.deleteAllByChatRoomId(roomId);
  }
```

**After** (L370~):
```java
  private void executeHardDelete(UUID roomId) {
    // 동시 퇴장으로 양쪽이 동시에 hard delete를 시도할 수 있으므로 이미 삭제된 방이면 건너뛴다 (멱등).
    if (!chatRoomRepository.existsById(roomId)) {
      log.debug("이미 완전 삭제된 채팅방입니다. 중복 hard delete를 건너뜁니다. roomId={}", roomId);
      return;
    }
    log.debug("채팅방에 다른 멤버가 나간 상태이므로, 채팅방을 완전 삭제합니다. roomId={}", roomId);
    chatRoomRepository.deleteById(roomId);
    log.debug("채팅방 메시지 삭제 : roomId={}", roomId);
    chatMessageRepository.deleteByChatRoomId(roomId);
    log.debug("채팅방 멤버 상태 삭제 : roomId={}", roomId);
    chatUserStateRepository.deleteAllByChatRoomId(roomId);
  }
```

> 참고: `adminForceDeleteChatRoom()`(L357)이 이미 동일한 `existsById` 가드를 호출 전에 두고 있어, 이 메서드로 가드를 내려도 기능 중복일 뿐 충돌 없음.

**검증**: 같은 roomId로 `executeHardDelete` 2회 연속 호출 시 두 번째가 예외 없이 no-op.

## 3. 현재 상태 (코드 인용)

- `ChatRoomService.java:325` — `handleRoomExit()`: 상대 상태 read → `isDeleted` 판단 → 내 상태 write의 비원자 패턴. race 원천.
- `ChatRoomService.java:337` — `if (opponentState.isDeleted())`: 동시 실행 시 둘 다 false를 봄.
- `ChatRoomService.java:370` — `executeHardDelete()`: 멱등 가드 없음. 단, `deleteById`/`deleteBy...`는 없는 대상이면 조용히 통과하므로 현재도 부분적으로는 안전.
- `ChatUserState.java:42` — `removeRoom()`: `removedAt = now()` 단순 setter.
- `ChatUserState.java:46` — `isDeleted()`: `removedAt != null` 여부.
- `ChatUserStateRepository.java:10` — 순수 `MongoRepository`. MongoTemplate 접근 수단 없음.
- `ChatMessageService.java:170` — `sendSystemMessage(room, leaverId, opponentState)`: 시그니처 변동 없음, 그대로 사용.
- 프로젝트 내 MongoTemplate 직접 사용처 0건(테스트 제외) — 본 변경이 첫 도입.

## 4. 위험 & 완화

- **위험**: MongoTemplate 빈이 컨텍스트에 없을 수 있음 → **완화**: `spring-boot-starter-data-mongodb` 사용 시 `MongoTemplate`은 자동 구성됨. 기존에 `MongoRepository`가 동작 중이므로 Mongo 인프라는 이미 존재 → 빈 보장됨.
- **위험**: `{RepositoryName}Impl` 명명 규약 위반 시 Spring Data가 fragment를 못 찾음 → **완화**: 클래스명 정확히 `ChatUserStateRepositoryImpl` 사용(Task 2에 반영).
- **위험**: `markRemovedIfNotRemoved`가 `@Transactional`(Postgres) 경계 밖에서 즉시 커밋됨 → **완화**: 의도된 동작. 원자성은 Mongo 단일 문서 연산이 보장하고, Postgres tradeRequestHistory 변경과 독립적이어도 무방(기존 코드도 Mongo write는 tx 밖이었음, 동작 동일성 유지).
- **위험**: soft delete 분기에서 `myState.removeRoom()` 제거로 시스템 메시지에 넘기는 상태 누락? → **완화**: `sendSystemMessage`에 넘기는 건 `opponentState`이지 내 상태가 아님. 내 상태는 원자 update로 이미 removed. 영향 없음.

## 5. 검증 방법

- [ ] **정상 흐름(순차 나가기)**: A 먼저 나가기 → A soft delete + 시스템 메시지 전송, 방 잔존. 이후 B 나가기 → B가 `opponentState(A).isDeleted()==true` 확인 → hard delete. 최종 DB에 ChatRoom·ChatMessage·ChatUserState 0건. — 수동/통합 테스트.
- [ ] **동시 나가기**: A·B `deleteRoom`을 두 스레드로 동시 호출. 결과: 최소 한쪽이 hard delete 실행, 다른 쪽은 `existsById==false`로 no-op. 최종 DB 완전 삭제. — 멀티스레드 통합 테스트(`CountDownLatch`로 동시 진입 유도).
- [ ] **중복 나가기**: A가 이미 나간 방을 A가 다시 나가기 → `markRemovedIfNotRemoved` null 반환 → early return, 예외 없음.
- [ ] **회귀**: 기존 `deleteRoom`, `deleteAllChatRoomsByMemberId`(L178, `handleRoomExit` 호출) 정상 동작 확인. — `mvn test -pl RomRom-Domain-Chat`(사용자 별도 환경).
- [ ] `adminForceDeleteChatRoom`(L357) 정상 동작 — `executeHardDelete` 가드 추가 후에도 동일.

## 6. 다음 단계

구현 방식을 선택하세요:

**1. Subagent-Driven (권장)** — `/suh-implement` 호출 시 태스크별 서브에이전트 + Self-Review 자동 진행
**2. Inline** — 현재 세션에서 순차 실행

> 병렬성 참고: Task 1→2→3은 의존 체인, Task 4·5는 동일 파일. 사실상 순차 작업이라 Inline도 부담 적음. 다만 Subagent-Driven은 Self-Review가 자동으로 붙는 이점.
