# 관리자 거래 상세 전용 페이지 + 전체 추적 설계

## 배경 / 문제

관리자 거래 관리 페이지(`/admin/trades`)의 현재 거래 상세는 **모달(팝업)** 방식이고, 다음이 불가능하다.

1. **물품 이미지가 안 보임** — 물품명/가격/카테고리만 텍스트로 표시. 관리자가 실제 물품을 눈으로 확인 못 함.
2. **채팅 내역 추적 불가** — 상세 모달에 채팅방 ID(UUID)만 덩그러니 표시. 두 사용자가 거래 과정에서 주고받은 실제 대화를 볼 수 없어, 분쟁/신고 발생 시 맥락 파악이 불가능.

관리자는 거래에 관련된 **모든 것**(물품 사진, 전체 채팅 내역)을 한 화면에서 확인할 수 있어야 한다.

## 목표

- 거래 상세를 **모달 → 전용 페이지**(`/admin/trades/{tradeRequestHistoryId}`)로 전환
- 양쪽 물품의 **이미지 갤러리** 표시
- 두 사용자가 주고받은 **채팅 전체 내역**(텍스트 + 이미지 + 위치 + 시스템 메시지)을 시간순으로 표시
- 강제 완료/취소 기능은 전용 페이지로 이전

## 비목표 (YAGNI)

- 채팅 메시지 검색/필터 (전체를 시간순으로 보여주는 것으로 충분)
- 채팅 메시지 페이지네이션 (대부분 거래 채팅은 짧음. 전체 로드로 단순화)
- 채팅 메시지 편집/삭제 (관리자는 조회만)
- 실시간 채팅 갱신 (정적 조회면 충분)

## 현황 (이미 존재하는 기반)

| 항목 | 위치 | 상태 |
|------|------|------|
| 전용 상세 페이지 패턴 | `item-detail.html` + `AdminPageController.itemDetail` (`/items/{itemId}`) | 그대로 따름 |
| 이미지 갤러리 렌더 | `item-detail.html`의 `renderGallery(itemImages)` | 재사용 |
| 물품 이미지 데이터 | `Item.itemImages` → `ItemImage.imageUrl` | 페치되어 응답에 포함됨 |
| 채팅 전체 조회 | `ChatMessageRepository.findByChatRoomIdOrderByCreatedDateAsc(chatRoomId)` | 그대로 사용 |
| 채팅 메시지 엔티티 | `ChatMessage`(Mongo): `senderId`, `recipientId`, `content`, `imageUrls`, `type`, `createdDate`, `latitude`, `longitude` | 그대로 응답 |
| 응답 DTO 필드 | `AdminResponse.AdminTradeDetailDto.chatMessages` 필드 | **현재 없음 → 추가 필요** |
| 거래 상세 조회 서비스 | `AdminTradeService.getTradeDetail` | 채팅 메시지 조회 추가 |
| MessageType | `TEXT, IMAGE, LOCATION, SYSTEM, TRADE_COMPLETE_REQUEST, TRADE_COMPLETE_REQUEST_CANCELED, TRADE_COMPLETE_REQUEST_REJECTED, TRADE_COMPLETED` | 타입별 렌더 분기 |

## 설계

### 1. 라우팅 / 컨트롤러 (모달 → 전용 페이지)

기존 `item-detail` 패턴과 동일하게 전용 페이지를 추가한다.

- `AdminPageController`에 `GET /admin/trades/{tradeRequestHistoryId}` 추가
  - `pageTitle="거래 상세"`, `currentMenu="trades"`, `tradeRequestHistoryId` 모델 주입
  - `return "admin/trade-detail"`
- 거래 목록(`trades.html`)에서 행 클릭 → `viewTradeDetail`을 모달 대신 `location.href = '/admin/trades/' + id` 로 변경
- 기존 모달(`tradeDetailModal`)은 `trades.html`에서 제거. 강제 완료/취소 모달(`forceModal`)도 trades.html에서 제거하고 trade-detail.html로 이전

### 2. 백엔드 데이터 (`getTradeDetail` 확장)

`AdminTradeService.getTradeDetail`에 채팅 메시지 조회를 추가한다.

```
1. 기존: 거래(findByTradeRequestHistoryIdWithItems) + 채팅방(findByTradeRequestHistoryId) 조회
2. 추가: 채팅방이 있으면 ChatMessageRepository.findByChatRoomIdOrderByCreatedDateAsc(chatRoom.chatRoomId)
         → 전체 메시지 시간순(오름차순) 조회. 채팅방 없으면 빈 리스트.
3. AdminTradeDetailDto에 chatMessages(List<ChatMessage>) 추가하여 반환
```

- `AdminResponse.AdminTradeDetailDto`에 `private List<ChatMessage> chatMessages;` 필드 추가
- 프로젝트 컨벤션대로 ChatMessage(Mongo) 엔티티를 DTO 변환 없이 그대로 응답에 포함
- 발신자 식별은 프론트에서 처리: `ChatMessage.senderId`(UUID)를 거래 양쪽 회원
  (`takeItem.member.memberId`, `giveItem.member.memberId`)과 비교해 닉네임/이메일로 표시.
  추가 쿼리 없음(회원 정보 이미 페치됨).

### 3. 화면 구성 (`trade-detail.html`)

`item-detail.html`의 레이아웃·갤러리·유틸 코드를 재사용한다.

```
┌─ 거래 상세 ──────────────────────── [채팅중] ─┐
│ ┌─ 주는 물품 ──────┐  ┌─ 받는 물품 ──────┐    │
│ │ [이미지 갤러리]   │  │ [이미지 갤러리]   │    │  itemImages 썸네일, 클릭 확대
│ │ 꼬질한 인형       │  │ 포켓몬 카드 R 교환 │    │
│ │ STAR_GOODS 3,000원│  │ ART_RARE 5,000원  │    │
│ │ 후민/hmking@...   │  │ 무결점-2315/chan@ │    │  물품ID 링크 → item-detail
│ └─────────────────┘  └─────────────────┘    │
│ ─────────────────────────────────────────── │
│ 거래 ID / 요청일 / 채팅방 ID                   │
│ ─────────────────────────────────────────── │
│ 💬 채팅 내역 (전체)                            │
│ ┌───────────────────────────────────────┐   │
│ │ [후민]      안녕하세요  09:12          │   │  좌/우 정렬로 발신자 구분
│ │          반갑습니다 [무결점-2315] 09:13 │   │  (giveItem 주인=좌, takeItem 주인=우)
│ │ [이미지 썸네일]                09:15    │   │  IMAGE 타입 = 사진 표시
│ │ ── 거래완료 요청 (시스템) ──            │   │  SYSTEM/TRADE_* = 중앙 배지
│ └───────────────────────────────────────┘   │
│              [강제 완료] [강제 취소] [목록]    │
└──────────────────────────────────────────────┘
```

**물품 영역**: item-detail의 `renderGallery` 재사용. 썸네일 나열 + 클릭 시 원본 확대.

**채팅 영역** — 메시지 타입별 렌더 분기:
- `TEXT`: 말풍선. 발신자에 따라 좌/우 정렬 + 발신자명 + 시각
- `IMAGE`: `imageUrls`를 썸네일로 표시 (클릭 확대)
- `LOCATION`: `latitude`/`longitude` 좌표 표시 (간단 텍스트 또는 지도 링크)
- `SYSTEM`, `TRADE_COMPLETE_REQUEST`, `TRADE_COMPLETE_REQUEST_CANCELED`, `TRADE_COMPLETE_REQUEST_REJECTED`, `TRADE_COMPLETED`: 중앙 정렬 시스템 배지
- 채팅방 없음/메시지 없음: "채팅 내역 없음" 안내

**발신자 좌우 정렬 규칙**: `giveItem.member` = 좌측, `takeItem.member` = 우측.
senderId가 둘 중 누구와도 안 맞으면(예: 탈퇴 회원) 발신자명을 senderId 앞 8자리로 fallback 표시.

**강제 완료/취소**: 페이지 하단 버튼. 클릭 시 사유 입력 모달(trade-detail.html 내 포함).
처리 후 목록(`/admin/trades`)으로 복귀.

### 4. Swagger Docs

`AdminApiControllerDocs`(또는 해당 Docs 인터페이스)의 `getTradeDetail` `@Operation` description에
chatMessages 필드 추가를 반영하고, `@ApiChangeLog` 최상단에 항목 추가
(date, author, issueNumber, description="거래 상세 응답에 채팅 전체 내역(chatMessages) 추가").

## 에러 처리

- 거래 없음: 기존대로 `TRADE_REQUEST_NOT_FOUND`
- 채팅방 없음: 빈 chatMessages 리스트 반환 (에러 아님)
- Mongo 조회 실패: 기존 GlobalExceptionHandler가 처리. 채팅 조회 실패가 거래 상세 전체를 막지 않도록, 채팅 조회는 best-effort로 감싸 빈 리스트 fallback 고려

## 테스트

- `AdminTradeService.getTradeDetail` 통합 테스트(`@ActiveProfiles("dev")`, 실 PostgreSQL + Mongo):
  - 채팅방이 있는 거래 → chatMessages가 시간순으로 채워져 반환되는지
  - 채팅방이 없는 거래(PENDING) → chatMessages가 빈 리스트인지
  - 거래 없음 → TRADE_REQUEST_NOT_FOUND

## 영향 범위 (파일)

| 파일 | 변경 |
|------|------|
| `AdminPageController.java` | `GET /admin/trades/{id}` 추가 |
| `AdminResponse.java` | `AdminTradeDetailDto.chatMessages` 필드 추가 |
| `AdminTradeService.java` | `getTradeDetail`에 채팅 메시지 조회 추가 |
| `trades.html` | 행 클릭 → 페이지 이동으로 변경, 모달 제거 |
| `trade-detail.html` | **신규** — 전용 상세 페이지 |
| `AdminApiControllerDocs.java` | Swagger description + ApiChangeLog |
| 테스트 신규 | `getTradeDetail` 통합 테스트 |
