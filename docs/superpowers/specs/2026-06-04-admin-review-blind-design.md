# Admin 후기 블라인드 관리 — 설계 문서

- 이슈: #771 (부모 Epic #707)
- 작성일: 2026-06-04
- 작성자: SUH SAECHAN

## 1. 배경 / 문제

거래 후기(`TradeReview`)는 현재 조회만 가능하고, 운영자가 부적절한 후기를 숨기거나 추적할 수단이 없다. 단순 Hard delete는 분쟁/소명 근거를 남기지 못한다. "누가·언제·왜 블라인드 처리했는지" 추적이 가능해야 하고, 일반 사용자에게는 후기 자리를 남긴 채 "관리자에 의해 블라인드 처리된 후기입니다" 안내로 치환해야 한다.

기존 물품 숨김(#712)은 `isAdminHidden`/`adminHideReason` 2필드만 있고 **처리자/시각 추적이 없다**. 후기는 추적 필드를 포함한다.

## 2. 공통 블라인드 묶음 (`@Embeddable BlindInfo`)

운영자 블라인드 처리는 후기뿐 아니라 다른 도메인에서도 재사용될 공통 관심사다. 4필드를 `@Embeddable` 묶음으로 추출한다.

`@MappedSuperclass`(상속)가 아니라 `@Embeddable`(품기)을 쓰는 이유: `TradeReview`는 이미 `BasePostgresEntity`를 상속 중이라 단일상속이 막혀 있고, 블라인드 정보는 엔티티의 "정체성"이 아니라 "부가 기능"이므로 품기가 의미상 맞다.

```java
// RomRom-Common
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class BlindInfo {
    private Boolean isBlinded = false;   // 블라인드 처리 여부 (Boolean is 접두사 규칙)
    private String blindReason;          // 블라인드 사유
    private UUID blindByAdminId;         // 처리한 관리자 식별자
    private LocalDateTime blindDate;     // 처리 시각
}
```

- 위치: `RomRom-Common` (여러 도메인 재사용 대비)
- 이번 적용 대상은 `TradeReview` **하나로 한정** (물품 통합은 별도 작업, 과설계 금지)
- DB: 별도 테이블 없이 `trade_review` 테이블에 4컬럼으로 펼쳐짐

### 처리 계약 `Blindable`

```java
public interface Blindable {
    void blind(String blindReason, UUID blindByAdminId);
    void unblind();
}
```

`TradeReview`가 구현 → admin 서비스가 엔티티 종류와 무관하게 동일 흐름으로 블라인드/해제 처리.

## 3. 엔티티 변경

```java
@Entity
public class TradeReview extends BasePostgresEntity implements Blindable {
    // ... 기존 필드 ...

    @Embedded
    private BlindInfo blindInfo = new BlindInfo();

    @Override
    public void blind(String blindReason, UUID blindByAdminId) {
        this.blindInfo.setIsBlinded(true);
        this.blindInfo.setBlindReason(blindReason);
        this.blindInfo.setBlindByAdminId(blindByAdminId);
        this.blindInfo.setBlindDate(LocalDateTime.now());
    }

    @Override
    public void unblind() {
        this.blindInfo.setIsBlinded(false);
        this.blindInfo.setBlindReason(null);
        this.blindInfo.setBlindByAdminId(null);
        this.blindInfo.setBlindDate(null);
    }
}
```

## 4. 마이그레이션 (Flyway)

`docs/flyway_guideline.md` 및 기존 `V1_4_60__add_item_admin_hidden_columns.sql` 패턴 100% 준수.

- 파일명: 구현 직전 `git pull` + `build.gradle` 버전 확인 후 확정 (가이드 §버전체계 필수). 후보 `V1_4_62__add_trade_review_blind_columns.sql`
- 컬럼별로 `테이블 존재(IF EXISTS) + 컬럼 부재(NOT EXISTS)` 가드 분리
- `is_blinded BOOLEAN NOT NULL DEFAULT false`, `blind_reason VARCHAR(500)`, `blind_by_admin_id UUID`, `blind_date TIMESTAMP`
- `DO $$ BEGIN ... EXCEPTION WHEN OTHERS THEN RAISE WARNING '오류 발생: %', SQLERRM; END $$;` 멱등 블록
- 여러 번 실행해도 안전(idempotent)

```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'is_blinded') THEN
        ALTER TABLE trade_review ADD COLUMN is_blinded BOOLEAN NOT NULL DEFAULT false;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_reason') THEN
        ALTER TABLE trade_review ADD COLUMN blind_reason VARCHAR(500);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_by_admin_id') THEN
        ALTER TABLE trade_review ADD COLUMN blind_by_admin_id UUID;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_date') THEN
        ALTER TABLE trade_review ADD COLUMN blind_date TIMESTAMP;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
```

## 5. 일반 사용자 노출 정책

- 블라인드된 후기 = **자리 남기고 문구 치환** (아예 제외하지 않음)
- `TradeReviewService.getReceivedTradeReviews` 응답 조립 시: `isBlinded == true`면 rating/comment/tags 를 마스킹하고 "관리자에 의해 블라인드 처리된 후기입니다" 안내 노출
- 카드 자체와 작성 시각은 보임
- 해당 사용자 API Docs `@ApiChangeLog` 갱신

### 평점 평균
- 현재 회원 평점 평균/통계 계산 기능이 코드에 **없음** → 이번 범위 아님
- 후기 조회 쿼리에 블라인드 구분을 두어, 추후 평균 기능 추가 시 블라인드 후기를 자동 제외할 수 있게 한다

## 6. Admin API

admin 컨벤션 준수: `POST` + `multipart/form-data` + `@ModelAttribute`, 공용 `AdminRequest`/`AdminResponse` (별도 DTO 금지), `success`/`message` 필드 없음(HTTP 상태코드 + CustomException).

| 엔드포인트 | 동작 |
|---|---|
| `POST /api/admin/reviews/list` | 평점/기간/블라인드여부 필터 + 페이지네이션. reviewer/reviewed/거래 deep-link |
| `POST /api/admin/reviews/blind` | `tradeReviewId` + `blindReason` → 블라인드 처리 (blindByAdminId/blindDate 기록) |
| `POST /api/admin/reviews/unblind` | `tradeReviewId` → 블라인드 해제 (필드 초기화) |

- `AdminReviewService` 신규 (`RomRom-Application/service`), `Blindable` 계약 기반
- `AdminReviewControllerDocs` 작성 + `@ApiChangeLog` 최상단 추가
- `AdminRequest`에 `tradeReviewId` / `blindReason` / 블라인드 필터 필드 추가
- `AdminResponse`에 `reviews`(Page<TradeReview>) 추가
- `TradeReviewRepository`에 관리자용 목록 쿼리(평점/기간/블라인드 필터) 추가

## 7. ErrorCode

- `TRADE_REVIEW_NOT_FOUND` (404) — 블라인드/해제 대상 후기 없음 (기존 `TRADE_REVIEW_*` 패턴 따름)

## 8. 권한 / 관리자 식별

- 블라인드/해제/목록 = `ROLE_ADMIN` 전용 (`/api/admin/**` 시큐리티 기적용)
- `blindByAdminId` = 처리 관리자 식별자. admin 인증 컨텍스트에서 추출 (기존 admin 인증 패턴 확인 후 적용)

## 9. Admin 화면

- `templates/admin/reviews.html` 신규 — 목록(평점/기간/블라인드 필터) + 블라인드 처리 모달(사유 입력) + 해제 버튼
- 블라인드된 행 = "🔒 블라인드 처리됨 (처리자/시각/사유)" 표시
- `templates/admin/layout.html` 좌측 메뉴에 "후기 관리"(currentMenu="reviews") 추가
- `AdminPageController`에 `/admin/reviews` GET 매핑 추가 → reviews.html 렌더
- #714 대시보드 "신규 후기" 카드 → 본 이슈 완료 후 `/admin/reviews` 링크 연결

## 10. 작업 범위

이번 #771 = **전체** (엔티티/마이그레이션/서비스/API/조회차단/admin 화면/메뉴/#714 연결). 평점 평균 집계는 기능 부재로 제외.

## 11. 검증

- 마이그레이션 재실행 멱등성 검증
- 블라인드 → 일반 사용자 API 노출 치환 검증 (rating/comment/tags 마스킹)
- 처리 이력(blindByAdminId/blindDate/blindReason) 기록 검증
- 블라인드/해제 토글 동작 검증
- 통합 테스트
