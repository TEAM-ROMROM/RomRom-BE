### 📌 작업 개요

DTO 클래스에 혼용되어 있던 `boolean` (primitive) 타입 필드를 `Boolean` (wrapper) 타입으로 통일. Java의 `boolean` → Lombok → Jackson 직렬화 과정에서 발생하는 JSON 키 `is` 접두어 제거 현상을 근본적으로 차단하고, `@JsonProperty` 없이도 `isXxx` 키가 안정적으로 유지되도록 개선.

**이슈**: [#604](https://github.com/TEAM-ROMROM/RomRom-BE/issues/604)

---

### 🔍 문제 분석

Java에서 `boolean` (primitive) 필드에 Lombok `@Getter`를 사용하면 `isXxx()` getter가 생성됨. Jackson은 이를 직렬화할 때 `is` 접두어를 제거하여 JSON 키가 `profanityDetected`처럼 변환됨.

반면 `Boolean` (wrapper) 필드는 `getIsXxx()` getter가 생성되고, Jackson은 `is` 접두어를 유지한 채 `isProfanityDetected`로 직렬화함.

이로 인해 `ChatMessagePayload.isProfanityDetected`가 WebSocket으로 전송될 때 JSON 키가 `profanityDetected`로 변환되어, 프론트엔드에서 `isProfanityDetected`로 파싱하는 로직과 불일치 발생.

일부 DTO에서는 이를 우회하기 위해 `@JsonProperty("isXxx")`를 붙여 키를 강제 지정하고 있었으나, 이는 근본 해결책이 아닌 임시 처리였음.

---

### ✅ 구현 내용

#### ChatMessagePayload.java
- **변경**: `boolean isProfanityDetected` → `Boolean isProfanityDetected`
- **변경**: `from(ChatMessage, boolean)` 파라미터 → `from(ChatMessage, Boolean)`
- **이유**: WebSocket 전송 시 JSON 키 `isProfanityDetected` 유지, 프론트엔드 파싱 정상화

#### ChatRoomResponse.java
- **변경**: `boolean isOpponentDeleted` → `Boolean isOpponentDeleted`
- **변경**: 불필요해진 `@JsonProperty("isOpponentDeleted")` 어노테이션 및 import 제거
- **이유**: `Boolean` wrapper로 전환하면 `@JsonProperty` 없이도 `isOpponentDeleted` 키 유지

#### ChatRoomRequest.java
- **변경**: `boolean isEntered` → `Boolean isEntered`
- **변경**: 불필요해진 `@JsonProperty("isEntered")` 어노테이션 및 import 제거
- **이유**: 동일 이유. 단 `@JsonIgnore`는 다른 필드에서 여전히 사용 중이므로 해당 import는 유지

#### AuthRequest.java
- **변경**: `boolean isMarketingInfoAgreed` → `Boolean isMarketingInfoAgreed`

#### TradeResponse.java
- **변경**: `boolean tradeRequestHistoryExists` → `Boolean tradeRequestHistoryExists`
- **비고**: `is` 접두어가 없는 필드라 Jackson 키 변환 이슈는 없었으나, 타입 일관성 확보를 위해 통일

---

### 🔧 주요 변경사항 상세

#### ChatRoomService.java - getter 호출부 수정

`Boolean` wrapper 전환으로 Lombok이 생성하는 getter 시그니처가 `isEntered()` → `getIsEntered()`로 변경됨. 이를 참조하던 `ChatRoomService.java:209`의 호출부를 `request.getIsEntered()`로 수정.

**특이사항**:
- `boolean` → `Boolean` 전환 시 기존에 `isXxx()` 형태로 getter를 호출하던 코드는 컴파일 에러 발생. 변경 후 전체 빌드 확인 필수.
- `Boolean` wrapper는 `null`을 가질 수 있으므로, 기존에 `false` 기본값에 의존하던 로직은 `null` 체크 필요 여부를 검토해야 함 (현재 해당 필드들은 빌더에서 명시적으로 `false` 세팅 또는 요청에서 필수 전달되는 구조이므로 문제 없음).

---

### 🧪 테스트 및 검증
- `./gradlew build -x test` 빌드 성공 확인
- WebSocket 채팅 메시지 발송 시 `isProfanityDetected` JSON 키 정상 수신 여부 확인 필요
- `isEntered` true/false 전달 시 채팅방 입/퇴장 처리 정상 동작 확인 필요

---

### 📌 참고사항
- `WebSocketProperties.java`, `AdminApiController.java` 내 `boolean` 필드는 Spring Boot `@ConfigurationProperties` 바인딩용이거나 내부 설정 클래스로, JSON 직렬화 대상이 아니므로 이번 변경 범위에서 제외
- 향후 신규 DTO 작성 시 `boolean` primitive 대신 `Boolean` wrapper 사용 원칙 준수 필요
