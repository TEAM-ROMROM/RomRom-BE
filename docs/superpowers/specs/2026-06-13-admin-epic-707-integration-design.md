# 관리자 페이지 전면 개편 (Epic #707) — #708 통합 + #709 + #713 설계

- **부모 Epic**: [#707 관리자 페이지 전면 개편](https://github.com/TEAM-ROMROM/RomRom-BE/issues/707)
- **포함 이슈**: [#708 회원 360 뷰 + 액션 통합](https://github.com/TEAM-ROMROM/RomRom-BE/issues/708), [#709 신고 처리 원스톱 워크플로우](https://github.com/TEAM-ROMROM/RomRom-BE/issues/709), [#713 회원 검색 강화 + 일괄 작업](https://github.com/TEAM-ROMROM/RomRom-BE/issues/713)
- **작성일**: 2026-06-13
- **작성자**: SUH SAECHAN
- **작업 기준 브랜치**: `main` (worktree 분리 없이 main에서 직접 작업)
- **이번 범위 제외**: #711 채팅 관리 (ChatMessage 스키마 변경·WebSocket 마스킹까지 범위가 커서 다음 단계로 분리)

---

## 1. 배경 / 문제 정의

관리자 페이지(Epic #707)는 도메인별 단순 조회 수준에 머물러 있어, 운영자가 회원·신고를 처리하려면 화면을 여러 번 옮겨 다녀야 한다. 본 설계는 세 이슈를 하나의 작업 단위로 묶어 main에 통합한다.

핵심 제약: **#708 회원 360 뷰는 `20260521_#707_관리자_페이지_전면_개편_Epic` worktree 브랜치에 이미 7커밋으로 대폭 구현돼 있다.** main에는 없다. 따라서 #708은 "재구현"이 아니라 "기존 구현을 충돌 없이 main으로 통합"하는 작업이다. #709/#713은 그 위에서 갭만 신규 구현한다.

---

## 2. 목표

1. **#708**: worktree의 회원 360 뷰 7커밋(12장 카드 응답 + sub-endpoint 10개 + 액션 3종 + 신규 MongoDB 도메인 2종)을 main에 충돌 없이 통합한다.
2. **#709**: 신고 상세에 피신고자·대상 요약과 누적 신고 카운트를 인라인 포함하고, 원클릭 처리 API(`reports/resolve`)로 정지/삭제/반려를 한 트랜잭션에서 수행한다.
3. **#713**: 회원 통합 검색(닉네임/이메일/memberId)을 강화하고, 다중 회원 일괄 정지/탈퇴 API를 부분 실패 개별 결과와 함께 제공한다.
4. 빌드·테스트 검증 후 이슈별로 커밋을 분리하고 리포트를 작성한다.

---

## 3. #708 통합 전략 (기존 7커밋 → main)

### 3.1 가져올 커밋 (worktree `20260521_#707...` 의 `984431a2` 이후 7개)

| 커밋 | 내용 |
|------|------|
| `8f01dd21` | LoginResult/AiUsageType/SanctionType enum, ErrorCode 3종, SanctionHistory 필드 확장 |
| `3223b260` | LoginHistory MongoDB 도메인 + AuthService 로그인 기록 인터셉터 |
| `0b4ca87f` | AiUsageHistory MongoDB 도메인 + @AiTracked AOP + Vertex/Category 호출 지점 어노테이션 |
| `07b16fd7` | AdminMemberService 360 카드 빌더/sub-list/액션 + Repository 메서드 보강 |
| `3a3a20a6` | AdminApiController 회원 360 endpoint 15개 추가 + Swagger 문서 |
| `5090cae0` | MongoDB 인덱스 초기화 + Postgres 인덱스 마이그레이션 |
| `cb1bd3d8` | 설계 spec 문서 |

### 3.2 통합 방식

단순 cherry-pick은 아래 파일에서 충돌이 확실하므로, **파일별로 main과 worktree 양쪽 내용을 읽어 수동 통합**한다. (브랜치 머지가 아니라 내용 병합 — main 히스토리를 깔끔하게 유지)

| 충돌 파일 | 통합 규칙 |
|-----------|-----------|
| `AdminApiController.java` | main 기존 엔드포인트 전부 유지 + #708의 회원 360 endpoint 15개를 members 섹션에 추가 |
| `AdminRequest.java` | main 필드 + #708 추가 필드(`itemIds`, `forceWithdrawReason`, `adminNotificationTitle/Content/Type`, `loginResult`, `aiUsageType`, `reportTargetType`, `tradeSide`) 합집합 |
| `AdminResponse.java` | main 필드 + #708의 `memberDetail360` 및 카드별 Page 필드 합집합 |
| `AdminMemberService.java` | main 메서드 유지 + #708의 360 빌더/sub-list/액션 메서드 추가 |
| `Member.java`, `MemberRepository.java` | main 변경 + #708 변경 합집합 |
| `ErrorCode.java` | main + #708의 3종(`MEMBER_ALREADY_DELETED`, `ADMIN_SELF_ACTION_FORBIDDEN`, `ADMIN_TARGET_FORBIDDEN`) 합집합 |
| `SanctionHistory.java` | main + #708의 `sanctionType`, `executorAdminId` 필드 합집합 |
| 신규 파일 (충돌 없음) | `AdminMemberDetail360Dto`, `BulkActionResult`, `AdminAsyncConfig`, `MongoIndexInitializer`, LoginHistory/AiUsageHistory 도메인 일체, `@AiTracked`/Aspect — 그대로 복사 |

### 3.3 Flyway 마이그레이션 리넘버링 (필수)

- **문제**: #708의 마이그레이션은 `V1_4_60__add_admin_member_360_indexes.sql`인데, main에는 이미 `V1_4_60__add_item_admin_hidden_columns.sql`이 존재한다. 같은 버전 = Flyway 충돌.
- **해결**: main 최신 마이그레이션은 `V1_4_64`이므로, #708 마이그레이션을 **`V1_4_65__add_admin_member_360_indexes.sql`**로 리넘버링한다.
- CLAUDE.md Flyway 규칙 준수: 모든 SQL은 테이블 존재 체크(`IF EXISTS`) + `DO $$ ... EXCEPTION WHEN OTHERS THEN RAISE WARNING ... END $$;` 멱등 블록 유지 (원본이 이미 준수하면 그대로).

---

## 4. #709 신고 처리 원스톱 — 갭 구현

현재 `reports/update-status`(상태만 변경)만 있고, 원클릭 처리·신고 상세 요약은 없다. SanctionHistory의 `reportId`/`reportType` 필드는 **이미 존재**(추가 보강 불필요).

### 4.1 신규 API

| Method | Path | Body | 동작 |
|--------|------|------|------|
| POST | `/api/admin/reports/resolve` | `reportId`, `reportType`(ITEM/MEMBER), `resolveAction`(SUSPEND_MEMBER/DELETE_ITEM/REJECT), 사유 필드 | 한 트랜잭션에서 신고 상태 변경 + 액션 실행 + 제재이력 연결 |

### 4.2 동작 (`AdminReportService.resolveReport`, `@Transactional`)

1. `resolveAction == SUSPEND_MEMBER`: 피신고자 정지(`AdminMemberService.suspendMember` 재사용, reportId 전달) → 신고 COMPLETED
2. `resolveAction == DELETE_ITEM`: 신고 대상 물품 삭제(`AdminItemService.deleteItemByAdmin` 재사용) → 신고 COMPLETED
3. `resolveAction == REJECT`: 신고 REJECTED 처리 (액션 없음)
4. 모든 경로에서 SanctionHistory에 reportId 연결 저장 (정지/삭제 시)

### 4.3 신고 상세 응답 보강

`reports/item-detail`, `reports/member-detail` 응답에 인라인 추가:
- 피신고자 요약 (memberId, 닉네임, 계정상태, 현재 정지 여부)
- 신고 대상 요약 (물품: itemId/itemName/상태, 회원: memberId/닉네임)
- **동일 피신고자에 대한 누적 신고 건수** (ItemReport+MemberReport 합산 count)

기존 단일 `AdminResponse`에 nullable 필드로 추가 (Admin 컨벤션 — 도메인별 DTO 분리 금지).

### 4.4 신규 enum

`ResolveAction { SUSPEND_MEMBER, DELETE_ITEM, REJECT }` — `RomRom-Common/.../constant/`. `AdminRequest.resolveAction` 필드 추가.

---

## 5. #713 회원 검색 강화 + 일괄 작업 — 갭 구현

### 5.1 검색 강화

현재 `MemberRepository.searchByKeywordAndIsDeletedFalse`는 nickname/email OR 매칭만 한다. **memberId(UUID 문자열) 매칭을 추가**한다. (전화번호는 Member 엔티티에 필드 자체가 없으므로 범위 외 — spec에 명시.)

```sql
WHERE m.isDeleted = false AND (
  LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
  LOWER(m.email)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR
  CAST(m.memberId AS string) LIKE LOWER(CONCAT('%', :keyword, '%'))
)
```

> 구현 시 확인: `CAST(... AS string)`은 JPQL 표준이나 Postgres UUID 캐스팅 동작을 통합 테스트로 검증한다. 동작 이슈 시 native query의 `m.member_id::text LIKE ...`로 대체.

### 5.2 일괄 작업 API

| Method | Path | Body | 동작 |
|--------|------|------|------|
| POST | `/api/admin/members/bulk-suspend` | `memberIds`(List<UUID>), `suspendReason`, `suspendedUntil` | 다중 정지 |
| POST | `/api/admin/members/bulk-withdraw` | `memberIds`(List<UUID>), `forceWithdrawReason` | 다중 강제 탈퇴 |

- `AdminRequest.memberIds`(List<UUID>) 필드 추가. `itemIds`는 #708이 이미 추가.
- 각 memberId를 개별 처리하고 **개별 성공/실패 결과를 `List<BulkActionResult>`로 응답** (targetId=memberId). #708의 `BulkActionResult` 재사용.
- 부분 실패 처리: 한 건 실패가 전체를 롤백하지 않도록 **회원 단위 트랜잭션**으로 처리(루프 내부 try/catch + 개별 트랜잭션 메서드 호출). 실패 건은 `failReason` 기록.
- 정지는 `AdminMemberService.suspendMember`, 탈퇴는 #708의 `forceWithdrawMember` 재사용.

---

## 6. 컴포넌트 / 데이터 흐름

```
AdminApiController (members/*, reports/*)
  └─ AdminMemberService   : 360 빌더, sub-list, suspend/unsuspend/force-withdraw/bulk-*, search
  └─ AdminReportService   : resolveReport(트랜잭션 오케스트레이션), 신고상세 요약
  └─ AdminItemService     : deleteItemByAdmin (resolve의 DELETE_ITEM에서 호출)
       ↓
  Repositories: Member/Item/TradeRequestHistory/ChatRoom/ItemReport/MemberReport/SanctionHistory
                /LoginHistory(mongo)/AiUsageHistory(mongo)/NotificationHistory/LikeHistory
```

- **단위 경계**: Controller는 라우팅만, Service가 트랜잭션·오케스트레이션, Repository가 조회. `resolveReport`는 다른 Service(Member/Item)를 조합하는 오케스트레이터 — 한 트랜잭션 안에서 호출.
- Entity 직접 반환 원칙(프로젝트 컨벤션) 유지 — DTO 매핑 최소화. 단 360 카드·요약·BulkActionResult처럼 집계가 필요한 경우만 응답 전용 객체 사용.

---

## 7. 에러 처리

- 모든 에러는 `CustomException` + `@ControllerAdvice`로 처리, `success`/`message` 필드 미사용(Admin 컨벤션).
- `resolveReport`: 신고/대상 미존재 → 기존 ErrorCode(`REPORT_NOT_FOUND` 등). 본인/타 관리자 대상 액션 → #708이 추가한 `ADMIN_SELF_ACTION_FORBIDDEN`/`ADMIN_TARGET_FORBIDDEN` 재사용.
- 일괄 작업: 개별 실패는 예외를 던지지 않고 `BulkActionResult.failReason`에 담아 계속 진행. 전체가 실패해도 200 + 결과 배열(운영자가 무엇이 실패했는지 확인).

---

## 8. 테스트

- `AdminReportServiceTest`: resolve(SUSPEND_MEMBER) → 신고 COMPLETED + SanctionHistory.reportId 저장 검증. resolve(DELETE_ITEM) → 물품 soft delete + 신고 COMPLETED. resolve(REJECT) → REJECTED.
- `AdminMemberServiceTest`(신규): 통합 검색 memberId 매칭, bulk-suspend 부분 실패(존재하지 않는 memberId 1건 포함 시 나머지 성공 + 실패건 failReason).
- #708 통합분은 worktree에 동반된 테스트(LoginHistoryServiceTest, AiUsageHistoryServiceTest 등)를 함께 가져와 통과 확인.
- 전체 `./gradlew build` 그린 확인 후 커밋.

---

## 9. 문서화 (Swagger Docs)

- API 변경 시 해당 `*ControllerDocs` 동시 수정 (CLAUDE.md 규칙).
- 신규 엔드포인트(`reports/resolve`, `members/bulk-suspend`, `members/bulk-withdraw`)와 #708 endpoint 15개에 `@ApiChangeLogs` 최상단 항목 추가 + `@Operation` description 갱신.

---

## 10. 커밋 / 마무리 계획

이슈별로 커밋 분리:
1. `#708` : 회원 360 뷰 통합 (7커밋 분량을 내용 병합해 1~2 커밋으로) — `회원_360_뷰_액션_통합 : feat : ...`
2. `#709` : 신고 처리 원스톱 — `신고_처리_원스톱_워크플로우 : feat : ...`
3. `#713` : 회원 검색 강화 + 일괄 작업 — `회원_검색_강화_일괄_작업 : feat : ...`

각 커밋 메시지에 이슈 링크 포함. 커밋 후 각 이슈에 `/report` 형식 리포트 댓글 작성. (push·라벨 변경·배포는 사용자 지시 시에만)
