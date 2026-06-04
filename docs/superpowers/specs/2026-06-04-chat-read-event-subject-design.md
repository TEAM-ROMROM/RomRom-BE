# 채팅 읽음 이벤트 채널 주체 명시 설계 (#765)

작성일: 2026-06-04
이슈: [#765](https://github.com/TEAM-ROMROM/RomRom-BE/issues/765) — [버그][채팅/WebSocket] 읽음 이벤트 채널에서 상황별로 다른 주체의 상태가 전송됨

## 문제

채팅 읽음 이벤트는 단일 WebSocket 채널 `chat.read.{roomId}` 로 전송된다.
payload는 `ChatUserState` 엔티티 그대로이며, 상황에 따라 **담기는 주체가 다르다**.

| 상황 | 호출 | payload 주체 | 의미 |
|------|------|------|------|
| 새 메시지 도착 | `ChatMessageService:250` `sendReadEvent(opponentState)` | 상대방 | "상대방이 읽음" |
| 방 입장 | `ChatRoomService:267` `sendReadEvent(myState)` | 입장자(나) | "내가 입장함" |

같은 채널로 서로 다른 주체의 state가 전달되어, 클라이언트가 payload의 `memberId`를
자신의 ID와 비교하는 **추측 분기**로 구분해야 한다 (`chat_room_screen.dart:400`).

### 현재 FE 처리 (오동작은 아님)

```dart
_readEventSubscription = _wsService.subscribeToReadEvents(...).listen((state) {
  if (state.memberId == _myMemberId || !mounted) return; // 내 state면 버림
  setState(() => _opponentState = state);                 // 상대 state면 읽음 반영
});
```

- 새 메시지 → opponentState 송출 → 수신자 화면에서 `memberId == 상대` → 반영
- 방 입장 → myState 송출 → 입장자 본인 화면에서 `memberId == 나` → 버림 / 상대방 화면에서 `memberId == 입장자(상대)` → 반영

기능상 결과는 맞지만, **memberId 추측에 의존**하는 점이 취약하고 혼선을 준다.

## 해결

payload에 `eventType` enum 필드를 명시한다. 클라이언트가 추측 대신 명시 신호로 분기.
엔티티 직접 전송 원칙(CLAUDE.md)을 유지하기 위해 **`@Transient` 직렬화 전용 필드**로 추가한다 (MongoDB 저장 제외).

## 변경 사항

### BE — `RomRom-Domain-Chat`

1. **신규 enum** `ChatReadEventType` (`com.romrom.chat.entity.mongo` 또는 적절한 enum 패키지)
   - `READ_RECEIPT` — 상대가 메시지를 읽음 (새 메시지 도착 시, opponentState)
   - `ROOM_ENTER` — 상대가 방에 입장함 (입장 시, myState 송출)

2. **`ChatUserState` 엔티티** — 직렬화 전용 필드 추가
   ```java
   @Transient
   private ChatReadEventType eventType; // DB 저장 안 됨, WS payload에만 실림
   ```
   세터 또는 빌더 기반으로 송출 직전 세팅.

3. **`ChatWebSocketService.sendReadEvent`** — eventType 파라미터 추가
   ```java
   public void sendReadEvent(ChatUserState payload, ChatReadEventType eventType) {
     payload.setEventType(eventType);
     String roomRoutingKey = "chat.read." + payload.getChatRoomId();
     String destination = "/exchange/" + chatRoutingProperties.getChatExchange() + "/" + roomRoutingKey;
     template.convertAndSend(destination, payload);
     log.debug("채팅 읽음 이벤트 브로커 송출 완료, eventType={}, destination={}", eventType, destination);
   }
   ```

4. **호출처 2곳**
   - `ChatMessageService:250` → `sendReadEvent(opponentState, ChatReadEventType.READ_RECEIPT)`
   - `ChatRoomService:267` → `sendReadEvent(myState, ChatReadEventType.ROOM_ENTER)`

5. **Swagger Docs** (CLAUDE.md 필수 규칙)
   - `ChatWebSocketControllerDocs` `@Operation` description에 `eventType` 필드 설명 + 예시 JSON 추가
   - `@ApiChangeLog` 배열 최상단에 항목 추가 (date `2026.06.04`, issueNumber `765`, description "읽음 이벤트 payload에 eventType 필드 추가")

### FE — `D:/0-suh/project/RomRom-FE/`

6. **`ChatUserState` 모델** `fromJson` / `toJson` 에 `eventType` 필드 추가 (nullable — 구버전/누락 호환)

7. **`chat_room_screen.dart` 읽음 이벤트 구독** 분기 명료화
   ```dart
   _readEventSubscription = _wsService.subscribeToReadEvents(widget.chatRoomId).listen((state) {
     if (!mounted) return;
     // READ_RECEIPT(상대가 읽음) / ROOM_ENTER(상대가 입장) 모두 상대 읽음커서 갱신
     // 내 state 방어(입장 본인 등)는 memberId로 유지
     if (state.memberId == _myMemberId) return;
     setState(() => _opponentState = state);
   });
   ```
   eventType이 명시되므로 디버그 로그/향후 분기 확장에 활용. memberId 방어는 안전망으로 잔존.

## 호환성

- `eventType`은 **추가 필드**. FE 구버전이 무시해도 기존 memberId 분기 동작 유지 → 점진 배포 안전.
- BE 먼저 배포해도 FE 깨지지 않음.

## 영향 없는 범위

- `ChatRoomService.getOpponentState` (REST 초기 조회) — 변경 없음
- MongoDB 스키마 / `@CompoundIndex` — `@Transient`라 영향 없음
- Flyway / PostgreSQL — 무관

## 테스트 관점

- 새 메시지 도착 시 payload `eventType == READ_RECEIPT`, memberId == 상대
- 방 입장 시 (상대 present) payload `eventType == ROOM_ENTER`, memberId == 입장자
- FE: 두 케이스 모두 상대 읽음커서 갱신, 본인 state 방어
