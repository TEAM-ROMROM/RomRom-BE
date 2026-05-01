### 📌 작업 개요
회원 탈퇴 시 `TradeRequestHistory` 삭제 과정에서 `ChatRoom`이 여전히 참조하고 있어 발생하는 Foreign Key 제약조건 위반 문제 수정

**보고서 파일**: `.report/20251219_#423_회원탈퇴시_ChatRoom_에_대한_Foreign_Key_제약조건_위반_문제_발생.md`

### 🔍 문제 분석
회원 삭제 API(`/api/members/delete`) 호출 시 다음 순서로 데이터 삭제가 진행됨:
1. Member 도메인 관련 데이터 삭제
2. Item 도메인 관련 데이터 삭제 (TradeRequestHistory 포함)
3. Auth 토큰 비활성화
4. 회원 삭제

문제는 `TradeRequestHistory`를 삭제할 때 `ChatRoom`이 `tradeRequestHistory`를 Foreign Key로 참조하고 있어 제약조건 위반이 발생함.

**에러 메시지**:
```
ERROR: update or delete on table "trade_request_history" violates foreign key constraint "fknh0lbb6idicc0mr9r3dghm55y" on table "chat_room"
Detail: Key (trade_request_history_id)=(76a832f0-87bd-414c-b323-470b95443844) is still referenced from table "chat_room".
```

### ✅ 구현 내용

#### ChatRoomRepository에 회원 ID로 조회 메서드 추가
- **파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/repository/postgres/ChatRoomRepository.java`
- **변경 내용**: `findByTradeReceiverMemberIdOrTradeSenderMemberId()` 메서드 추가
- **이유**: 회원이 참여한 모든 ChatRoom을 조회하기 위해 회원 ID로 검색하는 메서드 필요

#### ChatRoomService에 회원 관련 ChatRoom 일괄 삭제 메서드 추가
- **파일**: `RomRom-Domain-Chat/src/main/java/com/romrom/chat/service/ChatRoomService.java`
- **변경 내용**: `deleteAllChatRoomsByMemberId()` 메서드 추가
- **이유**: 회원 삭제 시 관련된 모든 ChatRoom을 삭제하기 위한 메서드 필요. ChatMessage, ChatUserState도 함께 삭제

#### MemberApplicationService에 ChatRoom 삭제 로직 추가
- **파일**: `RomRom-Application/src/main/java/com/romrom/application/service/MemberApplicationService.java`
- **변경 내용**: `deleteMember()` 메서드에 ChatRoom 삭제 로직 추가 및 삭제 순서 조정
- **이유**: TradeRequestHistory 삭제 전에 ChatRoom을 먼저 삭제하여 FK 제약조건 위반 방지

#### Application 모듈에 Chat 도메인 의존성 추가
- **파일**: `RomRom-Application/build.gradle`
- **변경 내용**: `api project(':RomRom-Domain-Chat')` 의존성 추가
- **이유**: Application 레이어에서 ChatRoomService 사용을 위해 필요

### 🔧 주요 변경사항 상세

#### ChatRoomRepository.findByTradeReceiverMemberIdOrTradeSenderMemberId()
회원이 `tradeReceiver` 또는 `tradeSender`로 참여한 모든 ChatRoom을 조회하는 메서드 추가.
JOIN FETCH로 연관 엔티티(tradeReceiver, tradeSender, tradeRequestHistory)를 함께 조회하여 N+1 문제 방지.

**쿼리**:
```java
@Query("SELECT c FROM ChatRoom c " +
    "JOIN FETCH c.tradeReceiver JOIN FETCH c.tradeSender JOIN FETCH c.tradeRequestHistory " +
    "WHERE c.tradeReceiver.memberId = :memberId OR c.tradeSender.memberId = :memberId")
List<ChatRoom> findByTradeReceiverMemberIdOrTradeSenderMemberId(UUID memberId);
```

#### ChatRoomService.deleteAllChatRoomsByMemberId()
회원 삭제 시 관련된 모든 ChatRoom을 삭제하는 메서드 추가.
각 ChatRoom에 대해 다음 순서로 삭제:
1. ChatMessage 삭제 (MongoDB)
2. ChatUserState 삭제 (MongoDB)
3. ChatRoom 삭제 (PostgreSQL)

**특이사항**:
- `@Transactional`로 전체가 하나의 트랜잭션으로 처리되어 중간 실패 시 롤백
- 삭제 과정을 로깅하여 디버깅 용이성 확보

#### MemberApplicationService.deleteMember() 삭제 순서 조정
회원 삭제 시 다음 순서로 데이터 삭제:
1. Member 도메인 관련 데이터 삭제
2. **Chat 도메인 관련 데이터 삭제** (새로 추가, TradeRequestHistory 삭제 전에 먼저 처리)
3. Item 도메인 관련 데이터 삭제 (TradeRequestHistory 포함)
4. Auth 토큰 비활성화
5. 회원 삭제

**핵심 변경**:
- ChatRoom 삭제를 Item 도메인 데이터 삭제 전에 수행하여 FK 제약조건 위반 방지
- `ChatRoomService` 의존성 주입 추가

### 📦 의존성 변경
- `RomRom-Application` 모듈에 `RomRom-Domain-Chat` 모듈 의존성 추가
- 기존 외부 라이브러리 의존성 변경 없음

### 🧪 테스트 및 검증
- 회원 삭제 API 호출 시 ChatRoom이 정상적으로 삭제되는지 확인
- TradeRequestHistory 삭제 시 FK 제약조건 위반이 발생하지 않는지 확인
- 양쪽 회원이 모두 삭제되는 경우 중복 삭제 방지 확인 필요
- 많은 ChatRoom을 가진 회원 삭제 시 성능 확인 필요

### 📌 참고사항
- **삭제 순서 중요**: ChatRoom → TradeRequestHistory 순서를 반드시 유지해야 함
- **트랜잭션 경계**: `@Transactional`로 전체가 하나의 트랜잭션으로 처리되어 중간 실패 시 전체 롤백
- **상대방 회원 영향**: 한쪽 회원 삭제 시 상대방의 ChatRoom도 함께 삭제됨
- **Hard Delete 방식**: 프로젝트 정책에 따라 ChatRoom은 Hard Delete 처리 (softDelete 아님)



