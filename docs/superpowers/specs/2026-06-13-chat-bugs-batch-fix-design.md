# 채팅 로직 버그 일괄 수정 설계

- **대상 이슈**: #753, #754, #755, #756, #757, #758, #763, #764, #765 (채팅 버그군)
- **작성일**: 2026-06-13
- **작성자**: SUH SAECHAN
- **작업 기준 브랜치**: `main`
- **제약**: **프론트엔드 코드는 수정하지 않는다.** FE 응답/payload 구조에 영향을 주는 변경은 이번 범위에서 제외한다.

---

## 1. 배경

채팅 도메인 버그 9건이 열려 있다. 코드를 직접 분석한 결과, 일부는 이미 해결돼 있고 일부는 FE 영향이 있어 제외 대상이다. FE 영향 없는 6건만 이번에 수정한다.

## 2. 분류 (코드 검증 결과)

### 2.1 이미 해결됨 — 코드 검증만 (수정 없음)
- **#763 SUBSCRIBE·SEND 세션속성 null NPE**: `CustomChannelInterceptor`의 CONNECT 단계(세션속성 null → `UNAUTHORIZED` 예외)와 `validatePrincipalExpiration`(SUBSCRIBE/SEND에서 sessionAttributes·customUserDetails 이중 null 체크)이 이미 구현됨. → 닫기만.
- **#758 미사용 ObjectMapper**: `CustomStompProtocolErrorHandler`, `CustomMessagingErrorHandler` 두 클래스에 ObjectMapper 필드 자체가 없음(이미 제거됨). → 닫기만.

### 2.2 이번 범위에서 제외 — FE 영향
- **#765 읽음 이벤트 채널 주체 혼선**: 수정하려면 읽음 이벤트 payload에 "읽은 주체(readMemberId)"를 명시하거나 전송 주체의 의미를 바꿔야 하는데, 둘 다 FE의 payload 해석을 바꾼다. **FE 미수정 원칙에 따라 제외.** 별도 이슈로 FE와 협의 후 진행.

### 2.3 이번 수정 대상 (FE 영향 없음) — 6건

| # | 버그 | 수정 위치 | FE 영향 |
|---|------|----------|---------|
| #756 | 토큰 만료 검증 이중 | `ChatWebSocketController` 컨트롤러 중복 검증 제거 | 없음(내부 중복 제거) |
| #753 | RabbitMQ 라우팅 키 오용 | `RabbitMqConfig` 바인딩 키 교체 | 없음(브로커 설정) |
| #757 | 채팅방 동시생성 500 | `ChatRoomService` 생성 시 예외 처리 | 없음(응답 동일, 500만 제거) |
| #764 | 페이지네이션 hasNext 오판 | `ChatRoomService` 목록 조회 hasNext 재계산 | 없음(필드 동일, 값만 정확) |
| #754 | 안읽은 메시지 N+1 + Stream내 DB쓰기 | `ChatRoomService`/`ChatMessageService`/Repository | 없음(응답 동일, 내부 최적화) |
| #755 | 멀티디바이스 연결종료 전체 퇴장 | 세션ID 메모리 추적 + 연결종료 처리 | 없음(payload 동일) |

---

## 3. 수정 상세

### 3.1 #756 토큰 만료 검증 이중 제거 (저위험)

- **현재**: `CustomChannelInterceptor`의 SEND 처리에서 `validatePrincipalExpiration(accessor)` 1회 + `ChatWebSocketController.send()`에서 `customUserDetails.validateExpiration()` 1회 = 이중.
- **수정**: 컨트롤러의 `customUserDetails.validateExpiration()` 호출 제거. 검증은 채널 인터셉터 단일 지점으로 일원화. (컨트롤러는 이미 인증된 세션의 customUserDetails를 그대로 사용)
- **위험**: 없음. 인터셉터가 SEND 전에 항상 먼저 실행되므로 검증 누락 없음.

### 3.2 #753 RabbitMQ 라우팅 키 (저위험)

- **현재**: `RabbitMqConfig.binding()`이 `BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY)`를 쓰는데, `ROUTING_KEY`가 `org.springframework.amqp.rabbit.support.micrometer.RabbitTemplateObservation.TemplateLowCardinalityTags.ROUTING_KEY`(Micrometer 메트릭 태그 enum)를 잘못 import한 것.
- **실제 라우팅 키 패턴**: 메시지 발행은 `chat.read.{roomId}`, `chat.room.{roomId}` 형태(`ChatWebSocketService`). TopicExchange 바인딩은 이를 모두 받는 와일드카드 `chat.*` 필요.
- **수정**: 잘못된 Micrometer enum import 제거 + 바인딩 키를 `"chat.*"`로 교체. (가능하면 `ChatRoutingProperties`의 exchange 이름 기반으로 구성)
- **위험**: STOMP relay 채팅은 별도 큐라 영향 없음. RabbitTemplate 직접 발행 시 큐 도달 정상화.

### 3.3 #757 채팅방 동시생성 500 (저위험)

- **현재**: `ChatRoomService` 채팅방 생성에서 `findByTradeRequestHistoryId` 존재 확인 통과 후 `save` → 동시 요청 시 두 번째가 `ChatRoom.tradeRequestHistory`의 `unique=true` 제약 위반 → `DataIntegrityViolationException` 미처리 → 500.
- **수정**: `save`를 try/catch로 감싸 `DataIntegrityViolationException` 포착 시 `findByTradeRequestHistoryId`로 기존 방을 재조회해 정상 반환. (이미 존재 = 의도된 동작)
- **위험**: 없음. 의도(기존 방 반환)에 부합. 재조회 실패 시에만 `CustomException`.

### 3.4 #764 hasNext 오판 (저위험)

- **현재**: `ChatRoomService` 목록 조회가 DB Slice를 가져온 뒤 나간 방을 서버 필터링하는데, `SliceImpl`에 원본 `chatRoomsSlice.hasNext()`를 그대로 넘김 → 필터링으로 줄어든 실제 개수와 불일치 → 빈 다음 페이지 요청.
- **수정**: 필터링 후 실제 반환 리스트 크기 + 원본 hasNext를 함께 고려해 hasNext 재계산. (필터링 후 `size >= pageSize`이거나 원본에 다음 페이지가 있으면서 채울 수 있는 경우)
- **위험**: 응답 필드 구조 동일(`hasNext` 값만 정확). FE 영향 없음.

### 3.5 #754 N+1 + Stream내 DB쓰기 (중위험)

- **현재**: 채팅방 목록 조회 시 각 방마다 `countByChatRoomIdAndCreatedDateAfterAndSenderIdNot` 개별 호출(N+1, 주석에도 명시). 또 Stream `map()` 내에서 `ChatUserState` 누락 방에 `save()` 부작용.
- **수정**:
  1. `ChatMessageRepository`에 배치 집계 메서드 추가(MongoDB aggregation `$group`)로 방 목록의 안읽은 수를 1쿼리로 조회. 기존 N+1 루프를 배치 결과 조회로 교체.
  2. Stream 내 `save()`를 Stream 밖으로 분리 — 누락 `ChatUserState`는 기존 `ChatUserStateEnsureService.ensureStates`(이미 존재) 단계에서 보장하도록 통합하고, map은 순수 읽기만.
- **위험**: 응답 동일. 집계 쿼리 정확성은 테스트로 검증. 빈 결과/leftAt null 케이스 주의.

### 3.6 #755 멀티디바이스 연결종료 전체 퇴장 (중위험, 세션 메모리 추적)

- **현재**: `ChatWebSocketDisconnectListener`가 연결 종료 시 `chatRoomService.leaveActiveChatRooms(memberId)`를 호출 → `findByMemberIdAndLeftAtIsNull(memberId)`로 **회원의 모든 활성 방**을 퇴장 처리 → 다른 기기에서 보던 방도 퇴장됨.
- **수정 (세션ID 메모리 추적 — MongoDB 스키마 변경 없음)**:
  1. WebSocket 세션이 "어느 방을 열람 중"인지를 메모리(`ConcurrentHashMap<sessionId, Set<roomId>>`)로 추적하는 컴포넌트(`ChatSessionRegistry`) 신설. SUBSCRIBE/입장 시 등록, 퇴장 시 해제.
  2. 연결 종료 시 해당 **sessionId가 열람하던 방만** 퇴장 처리. 같은 회원의 다른 세션이 같은 방을 보고 있으면 퇴장하지 않음.
  3. CONNECT 시 `accessor.getSessionId()`를 세션 속성에 저장해 disconnect 리스너에서 사용.
- **트레이드오프**: 서버 재시작 시 메모리 추적 소실 → 그 시점 활성 세션 정보 유실(재연결로 복구). 사용자 합의됨(허용).
- **위험**: WebSocket 세션 생명주기 정확히 따라야 함. payload/응답 구조 변경 없음(FE 영향 없음).

---

## 4. 컴포넌트 / 데이터 흐름

```
CustomChannelInterceptor (CONNECT: sessionId 세션속성 저장, SEND: 토큰검증 단일화)
ChatWebSocketDisconnectListener → ChatSessionRegistry(메모리) → 세션이 본 방만 퇴장
ChatRoomService (목록: 배치 unread 조회 + hasNext 재계산, 생성: 동시성 예외처리)
ChatMessageService / ChatMessageRepository (배치 aggregation unread)
RabbitMqConfig (바인딩 키 chat.*)
```

## 5. 에러 처리
- 모든 예외는 기존 `CustomException` + `@ControllerAdvice` 체계 유지.
- #757은 `DataIntegrityViolationException`을 의도된 흐름(기존 방 반환)으로 흡수.

## 6. 테스트
- #757: 동일 tradeRequestHistoryId로 동시 생성 시 둘 다 같은 방 반환(예외 미전파) 검증.
- #764: 삭제된 방 포함 페이지에서 hasNext가 실제 반환 크기와 일치 검증.
- #754: 배치 unread 집계가 N+1 결과와 동일 값 반환 + Stream 내 save 없음 검증.
- #755: 한 세션 종료 시 다른 세션이 보던 방은 유지됨 검증.
- #756/#753: 컴파일 + 기존 채팅 테스트 회귀 없음.
- 전체 `./gradlew build` 그린(기존 pre-existing 실패인 LogFileServiceTest 제외).

## 7. 커밋 / 마무리
- 이슈별 커밋 분리: #753, #754, #755, #756, #757, #764 각각.
- #763, #758은 "이미 해결됨" 검증 결과를 리포트로 남기고 닫기.
- #765는 FE 협의 필요로 이번 제외 — 이슈에 사유 코멘트.
- push·배포는 사용자 지시 시에만.
