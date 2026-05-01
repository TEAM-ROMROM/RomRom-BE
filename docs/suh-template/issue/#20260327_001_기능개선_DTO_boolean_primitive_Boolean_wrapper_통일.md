🚀[기능개선][백엔드][Chat][Auth][Item] DTO boolean primitive 타입을 Boolean wrapper 타입으로 통일

📝 현재 문제점
---

- Java에서 `boolean` (primitive) 타입 필드는 Lombok이 `isXxx()` getter를 생성하고, Jackson이 이를 직렬화할 때 `is` 접두어를 제거하여 JSON 키가 `profanityDetected`처럼 변환됨
- 반면 `Boolean` (wrapper) 타입 필드는 `getIsXxx()` getter가 생성되어 JSON 키에 `is` 접두어가 유지됨 (예: `isProfanityDetected`)
- 이로 인해 프론트엔드와의 JSON 키 불일치가 발생하여, `isProfanityDetected` 필드가 WebSocket 수신 시 정상적으로 파싱되지 않는 버그 발생
- DTO 내 `boolean` / `Boolean` 혼용으로 인해 어떤 필드가 어떤 JSON 키로 직렬화되는지 예측하기 어려움

🛠️ 해결 방안 / 제안 기능
---

- 모든 DTO (Request/Response/Payload) 내 `boolean` primitive 필드를 `Boolean` wrapper 타입으로 통일
- `Boolean` wrapper 타입 사용 시 `@JsonProperty`를 별도로 지정하지 않아도 `isXxx` JSON 키가 유지되므로, 기존에 이를 목적으로 붙어 있던 `@JsonProperty` 어노테이션 제거

⚙️ 작업 내용
---

- `ChatMessagePayload.java` - `isProfanityDetected` : `boolean` → `Boolean`
- `ChatRoomResponse.java` - `isOpponentDeleted` : `boolean` → `Boolean`, `@JsonProperty` 제거
- `ChatRoomRequest.java` - `isEntered` : `boolean` → `Boolean`, `@JsonProperty` 제거
- `AuthRequest.java` - `isMarketingInfoAgreed` : `boolean` → `Boolean`
- `TradeResponse.java` - `tradeRequestHistoryExists` : `boolean` → `Boolean`

🙋‍♂️ 담당자
---

- 백엔드: 이름
- 프론트엔드: 이름
- 디자인: 이름
