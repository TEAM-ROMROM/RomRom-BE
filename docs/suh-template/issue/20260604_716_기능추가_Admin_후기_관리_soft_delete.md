📝 현재 문제점
---

- 거래 후기(`TradeReview`) 엔티티는 현재 **조회만** 가능하고, 작성자/평점/태그/한마디를 담을 뿐 운영자가 개입할 수단이 전혀 없다.
- 부적절한 후기(욕설, 허위, 명예훼손 등)가 작성돼도 운영자가 **숨기거나 추적할 방법이 없다.**
- 단순 삭제(Hard delete)는 데이터가 사라져 분쟁/소명 시 근거를 남기지 못한다. "누가, 언제, 왜 숨겼는지" 추적이 가능해야 하고, 숨겨진 후기 자리에는 "보통 관리자에 의해 비공개 처리된 후기입니다" 같은 안내를 표시할 수 있어야 한다.
- admin 대시보드(#714)에는 "신규 후기 카운트"만 노출되고, 후기 목록/처리 화면이 없다.

🛠️ 해결 방안 / 제안 기능
---

`TradeReview` 에 **Soft delete(숨김) 인프라**를 추가하고, admin 후기 전용 목록/숨김 처리 화면을 만든다.

1. **Soft delete 필드 추가** — 실제 row는 보존하고 숨김 플래그로 노출만 차단. 처리 이력(처리자/시각/사유)을 함께 기록해 추적 가능하게 한다.
2. **후기 목록 페이지** — 평점/기간/숨김여부 필터, 작성자·대상자·거래 deep-link, 페이지네이션.
3. **숨김/숨김해제 처리** — 운영자가 사유를 입력해 후기를 비공개 처리하거나 복구한다.
4. **클라이언트 표시 정책** — 일반 사용자 API에서 숨김된 후기는 "관리자에 의해 비공개 처리된 후기입니다" 형태로 치환하거나 제외(노출 정책은 구현 단계에서 확정).

⚙️ 작업 내용
---

**Entity / Migration**
- [ ] `TradeReview` 에 필드 추가 (변수명 규칙·Boolean is 접두사 준수):
  - `isHidden` (Boolean) — 비공개 처리 여부
  - `hiddenReason` (String) — 비공개 처리 사유
  - `hiddenByAdminId` (UUID) — 처리한 관리자 식별자
  - `hiddenDate` (LocalDateTime) — 비공개 처리 시각
- [ ] Flyway 마이그레이션 작성 (`RomRom-Web/.../db/migration/V{ver}__add_trade_review_hidden_columns.sql`)
  - 테이블 존재 여부 + 컬럼 존재 여부 `IF EXISTS` 가드, `DO $$ ... EXCEPTION WHEN OTHERS THEN RAISE WARNING ... END $$;` 멱등 패턴 준수

**Backend**
- [ ] `AdminReviewService` 신규 (`RomRom-Application/service`)
- [ ] API (admin 컨벤션: POST + multipart/form-data + `@ModelAttribute`, 별도 DTO 금지):
  - `POST /api/admin/reviews/list` — 평점/기간/숨김여부 필터 + 페이지네이션
  - `POST /api/admin/reviews/hide` — `tradeReviewId` + `hiddenReason` 으로 비공개 처리
  - `POST /api/admin/reviews/unhide` — 비공개 해제 (필드 초기화)
- [ ] `AdminRequest` / `AdminResponse` 에 필요한 필드 추가
- [ ] `AdminReviewControllerDocs` 작성 + `@ApiChangeLog` 최상단 추가
- [ ] 일반 사용자 후기 조회 API: 숨김된 후기 노출 정책 적용 (치환 or 제외) + 해당 Docs 갱신

**Frontend**
- [ ] `templates/admin/reviews.html` 신규 — 목록/필터/숨김 처리 모달(사유 입력)
- [ ] 숨김된 후기 행은 "🔒 비공개 처리됨 (처리자/시각/사유)" 로 표시
- [ ] #714 대시보드의 "신규 후기" 카드 클릭 시 본 페이지로 연결

**검증**
- [ ] 마이그레이션 재실행 멱등성 검증
- [ ] 숨김 → 일반 사용자 API 노출 차단 검증
- [ ] 처리 이력(처리자/시각/사유) 기록 검증
- [ ] 통합 테스트

관련 이슈
---
- 부모 Epic: #707
- 선행/연계: #714 (대시보드 "신규 후기" 카드 → 본 페이지로 drill-down)

🙋‍♂️ 담당자
---
- 백엔드: SUH SAECHAN
- 프론트엔드: 미정
- 디자인: 미정
