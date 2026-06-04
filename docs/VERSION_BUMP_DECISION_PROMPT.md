# RomRom-BE 버전 변경 판단 프롬프트

## 목적

이 프롬프트는 RomRom-BE 프로젝트에서 코드 변경 후 **버전을 어떻게 올려야 하는지 (patch / minor / major)** 판단하기 위한 가이드입니다.
AI 에이전트 또는 개발자가 이 프롬프트를 참고하여 버전 변경 수준을 결정합니다.

---

## 프로젝트 컨텍스트

### 버전 체계
- **SemVer**: `major.minor.patch` (예: 1.4.63)
- **patch**: 자동 증가 (main 푸시 시 `PROJECT-VERSION-CONTROL.yaml`이 처리)
- **minor / major**: 수동 변경 (`version.yml`에서 직접 수정)

### 배포 환경
- Flutter 앱이 iOS/Android 앱스토어에 배포되어 있음
- BE 서버와 앱 버전은 **완전히 독립적**으로 관리됨
- `app.min.version` 설정으로 특정 버전 미만의 앱에 강제 업데이트 화면을 표시함
- `app.min.version` 업데이트는 **사람이 수동으로 판단**하여 수행 (자동화 금지)

### API 특성
- 거의 모든 엔드포인트가 `POST` 메서드 사용
- `Content-Type`: 대부분 `multipart/form-data` (`@ModelAttribute`), 일부 `application/json`
- 응답: `ResponseEntity<XxxResponse>` 형태, Response 안에 **JPA Entity를 직접 포함**하는 하이브리드 패턴
- WebSocket: STOMP 프로토콜, RabbitMQ 브로커, 8가지 메시지 타입
- 인증: Firebase ID Token 검증 → JWT (AccessToken 30분, RefreshToken 7일)
- 에러: `ErrorCode` enum (100개 이상) + `GlobalExceptionHandler` (@RestControllerAdvice)

---

## 판단 기준

### 핵심 질문 (한 줄)

> **"이 변경을 배포했을 때, 앱스토어에서 구버전 앱을 사용하는 사람이 에러를 만나거나 기능이 동작하지 않는가?"**
>
> - **YES** → `minor` (또는 규모에 따라 `major`)
> - **NO** → `patch` (자동 처리됨, 별도 작업 불필요)

---

## patch (자동) — 하위호환이 유지되는 변경

아래에 해당하면 patch입니다. **별도 조치 불필요** (main 푸시 시 자동 증가).

### 1. API 응답에 필드가 추가되는 경우
- Response DTO에 새 필드 추가 (기존 앱은 새 필드를 무시함)
- Entity에 새 컬럼 추가 후 API 응답에 포함 (nullable이므로 기존 앱 영향 없음)
- 예: `ItemResponse`에 `viewCount` 필드 추가

### 2. 새 API 엔드포인트 추가
- 기존 엔드포인트는 그대로, 새 엔드포인트만 추가
- 예: `POST /api/item/bookmark` 신규 추가

### 3. 내부 로직 변경 (API 인터페이스 동일)
- Service 계층 비즈니스 로직 수정
- 쿼리 최적화, N+1 해결
- 캐시 전략 변경
- 로깅 추가/수정

### 4. 버그 수정 (API 인터페이스 동일)
- 기존에 잘못 동작하던 것을 고침
- 예외 처리 보강
- 데이터 정합성 수정

### 5. DB 변경이지만 API 영향 없는 경우
- nullable 컬럼 추가 (Flyway 마이그레이션)
- 인덱스 추가/삭제
- 내부 전용 테이블 추가 (system_config 등)
- 기존 컬럼에 기본값 추가

### 6. 새 ErrorCode 추가
- 기존 에러 코드는 유지, 새 에러 코드만 추가
- 예: `BOOKMARK_NOT_FOUND` 추가

### 7. 문서/설정 변경
- Swagger 문서 업데이트
- application.yml 설정 변경
- CI/CD 워크플로우 수정

---

## minor (수동) — 구버전 앱이 깨지는 변경

아래 중 **하나라도 해당**하면 minor입니다. `version.yml`에서 minor를 올리고 patch를 0으로 리셋하세요.

### 카테고리 1: API 응답 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 응답 필드 제거 | `ItemResponse`에서 `itemPrice` 필드 삭제 | 앱이 해당 필드를 읽으려다 null/에러 |
| 응답 필드 이름 변경 | `itemName` → `productName` | 앱이 기존 키로 파싱 실패 |
| 응답 필드 타입 변경 | `price: "1000"` (String) → `price: 1000` (Number) | 앱의 타입 파싱 실패 |
| 날짜/시간 포맷 변경 | `2026-04-02` → `2026-04-02T00:00:00Z` | 앱의 날짜 파서 실패 |
| null 허용 정책 변경 | 항상 값 있던 필드가 null로 올 수 있게 됨 | 앱이 non-null 가정으로 NPE |
| 응답 구조 변경 | 단건 객체 → 배열, 또는 래핑 구조 변경 | 앱의 JSON 파싱 구조 불일치 |
| 페이징 구조 변경 | `Page<Item>` → `Slice<Item>`, 또는 페이징 메타 필드 변경 | 앱의 페이징 처리 로직 깨짐 |
| Entity 필드 이름 변경 | Entity를 직접 반환하므로 Entity 필드명 변경 = API 응답 필드명 변경 | **RomRom 특수**: Entity 직접 반환 패턴 때문에 Entity 리팩토링이 곧 API 변경 |

### 카테고리 2: API 요청 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 필수 파라미터 추가 | `ItemRequest`에 `categoryId` 필수 추가 | 구버전 앱이 해당 값 안 보냄 → 400 에러 |
| 파라미터 이름 변경 | `itemName` → `productName` | 구버전 앱이 기존 이름으로 보냄 → 서버에서 null |
| 파라미터 타입 변경 | `itemId: String` → `itemId: Long` | 타입 불일치로 400 에러 |
| Content-Type 변경 | `multipart/form-data` → `application/json` 또는 반대 | 앱의 요청 형식 불일치 → 415 에러 |
| 요청 Body 구조 변경 | flat → nested (예: `{ "lat": 1, "lng": 2 }` → `{ "location": { "lat": 1, "lng": 2 } }`) | 앱의 요청 구조 불일치 |

### 카테고리 3: API 엔드포인트 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 경로 변경 | `/api/items/get` → `/api/products/get` | 앱이 기존 경로로 요청 → 404 |
| 경로 제거 | `/api/item/legacy-list` 삭제 | 앱이 해당 API 호출 → 404 |
| HTTP 메서드 변경 | `POST /api/item/get` → `GET /api/item/{id}` | 앱이 POST로 요청 → 405 |

### 카테고리 4: Enum/상태 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| Enum 값 이름 변경 | `TradeStatus.PENDING` → `TradeStatus.WAITING` | 앱이 "PENDING" 문자열로 비교/파싱 |
| Enum 값 제거 | `ItemCategory`에서 특정 카테고리 삭제 | 앱이 해당 값 사용 시 에러 |
| 상태 전이 흐름 변경 | 물품 상태가 `AVAILABLE → TRADING → COMPLETED`에서 중간 단계 추가/제거 | 앱의 상태 기반 UI 로직 깨짐 |
| AccountStatus 변경 | 새 상태 추가는 patch지만, 기존 상태 제거/이름 변경은 minor | 앱의 상태 분기 로직 |

### 카테고리 5: WebSocket/실시간 통신 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 메시지 페이로드 구조 변경 | `ChatMessagePayload`에서 필드 제거/이름 변경 | 앱의 채팅 메시지 파싱 실패 |
| 메시지 타입(MessageType) 변경/제거 | `TEXT` → `PLAIN_TEXT` | 앱의 메시지 타입 분기 로직 깨짐 |
| 구독/발행 경로 변경 | `/sub/chat.room.{id}` → `/topic/chat.{id}` | 앱의 STOMP 구독 실패 |
| STOMP 엔드포인트 변경 | `ws://api.romrom.xyz/chat` → `ws://api.romrom.xyz/ws` | 앱의 WebSocket 연결 실패 |
| 읽음 상태(ChatUserState) 구조 변경 | 필드 제거/이름 변경 | 앱의 읽음 표시 로직 깨짐 |

### 카테고리 6: 에러 응답 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 에러 응답 JSON 구조 변경 | `{ "errorCode": "...", "errorMessage": "..." }` → `{ "error": { "code": "...", "message": "..." } }` | 앱의 에러 파싱 로직 깨짐 |
| HTTP 상태 코드 의미 변경 | 기존 200으로 주던 응답을 201로 변경 | 앱이 200만 성공으로 처리하는 경우 |
| 특수 에러 응답 구조 변경 | `SuspendedMemberResponse`, `UgcViolationResponse` 등의 필드 변경 | 앱의 특수 에러 처리 UI 깨짐 |

### 카테고리 7: 인증/보안 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| JWT 클레임 구조 변경 | 클레임 키 변경, 필수 클레임 추가 | 기존 발급된 토큰으로 인증 실패 |
| 토큰 만료 시간 대폭 단축 | AccessToken 30분 → 5분 | 앱 UX 급격히 나빠짐 (빈번한 재인증) |
| 권한 체계 변경 | 기존 USER 권한으로 접근 가능하던 API에 새 권한 필요 | 앱이 해당 API 호출 시 403 |
| 비인증 API → 인증 필수 변경 | AUTH_WHITELIST에서 제거 | 앱이 토큰 없이 호출 → 401 |
| Firebase 인증 방식 변경 | providerId 검증 방식 변경 | 앱의 로그인 플로우 깨짐 |

### 카테고리 8: 파일/미디어 변경

| 변경 유형 | 구체적 예시 | 왜 깨지는가 |
|---|---|---|
| 이미지 URL 패턴 변경 | MinIO 경로 구조 변경, 도메인 변경 | 앱에 저장된 기존 이미지 URL 무효화 |
| 업로드 제한 강화 | 파일 크기 10MB → 5MB, 지원 형식 축소 | 앱에서 업로드 실패 (기존엔 가능했던 파일) |
| 파일 응답 형식 변경 | 이미지 URL 반환 방식 변경 | 앱의 이미지 로딩 실패 |

---

## major (수동) — 전면 재설계 수준의 변경

아래에 해당하면 major입니다. `version.yml`에서 major를 올리고 minor, patch를 0으로 리셋하세요.

| 변경 유형 | 구체적 예시 |
|---|---|
| 인증 방식 전면 변경 | Firebase → 자체 OAuth 서버 전환 |
| API 전체 구조 재설계 | URL 체계 전면 변경, 버저닝 도입 (`/v2/api/...`) |
| 대규모 도메인 리모델링 | Entity 구조 전면 재설계로 대부분의 API 응답 변경 |
| 통신 프로토콜 변경 | REST → GraphQL 전환, STOMP → Socket.IO 전환 |
| 데이터 포맷 전면 변경 | JSON → Protocol Buffers 등 |

---

## 판단 플로우차트

```
변경사항 발생
    │
    ▼
[1] API 엔드포인트 경로/메서드가 변경되었는가?
    ├─ YES → minor
    │
[2] API 응답에서 기존 필드가 제거/이름변경/타입변경 되었는가?
    ├─ YES → minor
    │
[3] API 요청에 새 필수 파라미터가 추가되었는가?
    ├─ YES → minor
    │
[4] 요청 Content-Type이 변경되었는가?
    ├─ YES → minor
    │
[5] Enum 값이 제거/이름변경 되었는가?
    ├─ YES → minor
    │
[6] WebSocket 메시지 구조/경로가 변경되었는가?
    ├─ YES → minor
    │
[7] 에러 응답 JSON 구조가 변경되었는가?
    ├─ YES → minor
    │
[8] 인증/권한 요구사항이 강화되었는가?
    ├─ YES → minor
    │
[9] 이미지/파일 URL 패턴이 변경되었는가?
    ├─ YES → minor
    │
[10] 위 모든 항목에 해당하지 않는가?
    └─ YES → patch (자동 처리, 별도 조치 불필요)
```

---

## ⚠️ RomRom 프로젝트 특수 주의사항

### Entity 직접 반환 패턴
RomRom-BE는 Response DTO 안에 **JPA Entity를 직접 포함**하여 반환합니다.

```
MemberResponse { Member member; ... }
ItemResponse { Item item; Page<Item> itemPage; ... }
ChatRoomResponse { ChatRoom chatRoom; Slice<ChatMessage> messages; ... }
```

**이것이 의미하는 것:**
- **Entity 필드명 변경 = API 응답 필드명 변경**입니다.
- 일반적인 프로젝트에서는 Entity 리팩토링이 내부 작업이지만, RomRom에서는 **API breaking change**가 됩니다.
- Entity에 `@JsonProperty`, `@JsonIgnore` 등을 사용하여 직렬화를 제어하는 경우, 이 어노테이션의 변경도 API 변경에 해당합니다.

### Boolean 필드 직렬화
- RomRom은 Boolean 필드에 `is` 접두사를 사용합니다 (예: `isLiked`, `isFirstLogin`).
- Jackson은 `Boolean isLiked`를 `"isLiked"`로 직렬화합니다.
- 만약 `isLiked` → `liked`로 변경하면 API 응답 필드명이 바뀌므로 **minor**입니다.

### Admin API vs User API
- Admin API (`/api/admin/**`)는 관리자 웹에서만 사용하므로, Admin API 변경은 **앱에 영향 없음** → **patch**로 충분합니다.
- 단, User API (`/api/auth/**`, `/api/item/**`, `/api/members/**`, `/api/chat/**`, `/api/trade/**` 등)의 변경은 앱에 직접 영향 → 위 기준대로 판단합니다.

### WebSocket은 특히 주의
- 채팅 기능은 실시간 통신이므로, 페이로드 변경 시 **즉시** 구버전 앱이 영향받습니다.
- REST API는 특정 화면 진입 시에만 호출되지만, WebSocket은 **채팅방에 있는 동안 계속 영향**받습니다.

### ErrorCode 제거 vs 추가
- **추가**: patch (앱은 모르는 에러코드를 기본 에러로 처리하면 됨)
- **제거/이름 변경**: minor (앱이 특정 에러코드에 대해 분기 처리하고 있을 수 있음)

---

## 판단 예시

### 예시 1: Item Entity에 viewCount 컬럼 추가
```
변경: Item Entity에 Integer viewCount 필드 추가
     Flyway: ALTER TABLE item ADD COLUMN view_count INTEGER DEFAULT 0

→ [1] 엔드포인트 변경? NO
→ [2] 기존 필드 제거/변경? NO (추가만 됨)
→ [3] 필수 파라미터 추가? NO
→ 결론: patch
```

### 예시 2: TradeStatus.PENDING → TradeStatus.WAITING 이름 변경
```
변경: TradeStatus enum에서 PENDING을 WAITING으로 변경

→ [5] Enum 값 이름 변경? YES
→ 결론: minor
→ 이유: 앱이 "PENDING" 문자열로 거래 상태를 판단하고 있을 수 있음
```

### 예시 3: 채팅 메시지에 reactions 필드 추가
```
변경: ChatMessagePayload에 List<Reaction> reactions 필드 추가

→ [6] WebSocket 메시지 구조 변경? 필드 추가만 (제거/이름변경 아님)
→ 결론: patch
→ 이유: 구버전 앱은 reactions 필드를 무시하면 됨
```

### 예시 4: /api/item/get → /api/item/detail 경로 변경
```
변경: 물품 상세 조회 API 경로 변경

→ [1] 엔드포인트 경로 변경? YES
→ 결론: minor
→ 이유: 구버전 앱이 /api/item/get으로 요청 → 404
```

### 예시 5: Admin 공지사항 API 응답 구조 변경
```
변경: AdminResponse에서 announcements 필드 구조 변경

→ Admin API인가? YES (/api/admin/**)
→ 결론: patch
→ 이유: Admin API는 관리자 웹에서만 사용, 앱에 영향 없음
```

### 예시 6: Item Entity의 itemName → productName 필드명 변경
```
변경: Item.java에서 필드명 변경 + Flyway로 컬럼명 변경

→ [2] 기존 필드 이름 변경? YES
→ 결론: minor
→ 주의: Entity 직접 반환 패턴이므로 Entity 필드명 = API 응답 필드명
```

### 예시 7: 물품 등록 API에 locationId 필수 파라미터 추가
```
변경: ItemRequest에 @NotNull UUID locationId 추가

→ [3] 필수 파라미터 추가? YES
→ 결론: minor
→ 이유: 구버전 앱이 locationId 안 보냄 → 400 에러
```

### 예시 8: JWT AccessToken 만료시간 30분 → 15분
```
변경: JwtUtil에서 ACCESS_TOKEN_EXPIRE_TIME 변경

→ [8] 인증 요구사항 강화? 만료시간 단축은 UX 변경이지만 기능이 깨지진 않음
→ 결론: patch (단, 5분 이하로 극단적 단축 시 minor 고려)
```

---

## 버전 변경 후 체크리스트

### minor 변경 시
- [ ] `version.yml`에서 minor 올리고 patch를 0으로 리셋 (예: 1.4.63 → 1.5.0)
- [ ] `build.gradle`의 version도 동일하게 변경
- [ ] 프론트엔드(Flutter) 팀에 breaking change 내용 공유
- [ ] Swagger Docs (`*ControllerDocs.java`) 업데이트
- [ ] `@ApiChangeLog` 추가
- [ ] 배포 후 `app.min.version` 업데이트 여부를 **사람이 판단**

### major 변경 시
- [ ] minor 체크리스트 전체 수행
- [ ] 마이그레이션 플랜 수립 (구버전 → 신버전 전환 계획)
- [ ] 앱스토어 강제 업데이트 일정 조율
- [ ] CHANGELOG에 breaking changes 상세 기록
