# 백엔드 전반 품질 개선 로드맵 설계

> 2026-10-29 진단 기반 (감사: `docs/architecture/audits/2026-10-29_백엔드_전체_아키텍처_감사.md`).
> FE 로드맵(RomRom-FE `2026-07-27-codebase-quality-improvement-design.md`, #936~#938)과 같은 방식.
> 서브프로젝트 5개(A→E)로 분해, 각각 별도 이슈·커밋 단위로 진행.

## 원칙 (전 서브프로젝트 공통)
- **FE 계약 불변**: enum 상수명·code, 에러코드·에러 응답 필드, JSON 키, 엔드포인트, multipart 키, STOMP 경로를 바꾸지 않는다.
  특히 `boolean isX` ↔ `Boolean isX` 변경 금지 (JSON 키가 `x` ↔ `isX`로 바뀜)
- CLAUDE.md 팀 결정 유지: Entity 직접 응답, 단일 `AdminRequest`/`AdminResponse`, Admin API `POST` + multipart
- API 동작이 바뀌면 `*ControllerDocs` + `@ApiChangeLog` 동시 수정
- 검증: `./gradlew compileJava compileTestJava` + 변경 모듈 테스트 통과

| 서브 | 성격 | 규모 | 위험도 |
|---|---|---|---|
| A. 운영 노출 테스트 API·토큰 로그 차단 | 버그(보안) | 2파일 + 로그 4곳 | 낮음 |
| B. 트랜잭션·권한 정합성 | 버그 | Admin 회원/물품, 임베딩, 로그인, 필터 | 중간 |
| C. 죽은 코드 제거·단일 원본화·최적화 | 개선 | 키/상수/사본/미사용 코드 | 낮음 |
| D. 대형 파일 분해 | 개선 | 1000줄 안팎 5개 | 중간 (동작 불변) |
| E. 아키텍처 규칙 테스트·위반 동결 | 개선 | 규칙 5개 | 낮음 |

---

## A — 운영 노출 테스트 API·토큰 로그 차단
- `TestController`, `TestService`에 `@Profile("!prod")` → 운영에서는 빈 미등록(404). dev·test(PR 미리보기)·로컬은 유지
- 토큰 원문 로그 제거: `AuthService.java:210`, `JwtUtil.java:151,251,269` → 토큰 값 대신 memberId·사유만
- 하지 않는 것: TestController 기능 변경, permitAll 목록 재구성(C에서)

## B — 트랜잭션·권한 정합성
1. **관리자 강제탈퇴**(`forceWithdrawMember`): 하위 `@Transactional` 호출 예외를 catch 후 진행하는 구조 제거 → 실패 시 전체 실패로 일관화, Mongo 제재 이력은 성공 후 기록
2. **물품 일괄삭제**(`bulkDeleteItems`): 회원 일괄 처리와 같은 건별 트랜잭션(self 주입) + `BulkActionResult` 실패 사유
3. **정지/일괄 정지**: `validateAdminAction` 적용(관리자 대상 차단), `executorAdminId` 이력 기록
4. **로그인 이력 total**: 필터를 쿼리로 내려 total 정확화
5. **날짜 파싱 4사본 → 공통 1개**, 잘못된 날짜는 400(`INVALID_REQUEST`)으로 통일
6. **`ResponseStatusException` → `CustomException`**, 관리자 필터의 `{"error":...}` 직접 응답 → ErrorCode 기반 공통 응답
7. **필터 `new ObjectMapper()` → JacksonConfig 빈 주입** (`suspendedUntil` ISO 직렬화 보장)
8. **임베딩**: DB 예외는 삼키지 않고 AI 호출 실패만 격리, 아이템 저장 트랜잭션 밖(AFTER_COMMIT)으로 이동
9. **스토리지 파일 삭제를 커밋 이후로** (`ItemService.java:199-205,744-749`)
10. **`AuthService.login()`**: `@Transactional` + 단계별 private 메서드(조회 > 플랫폼 검사 > 신규 생성 > 정지 해제 > 토큰 발급), 중복 builder 1개로
11. `MinioUtil` 초기화 실패 시 기동 실패(fail-fast)

## C — 죽은 코드 제거·단일 원본화·최적화
- 제거: `common/dto/deprecated/` (Aspect 2개, `LogUtil` + 테스트), `ItemService.deleteRelatedItemInfo`·`getAllItems`, `EmbeddingService` 더미 메서드, `SecurityConfig` 미사용 필드·`ALLOWED_ORIGINS`
- 설정 키 레지스트리: `SystemConfig` 키 문자열을 상수 한 곳으로(`server.maintenance.*`, `ai.*`, `app.*`, `image.*`, `admin.alert.*`, `ugc.filter.patterns`) + 숫자 설정 파싱 공통 메서드
- Redis 키 접두사(`RT:`, `RT:ADMIN:`, `BL:`) 한 곳으로
- `extractItemText` 3사본 → 1개, 서명 헤더 상수 1곳
- `ADMIN_PATHS` → `/api/admin/**` 패턴
- 최적화: `AdminItemService` 반복문 쿼리(N+1) 일괄 조회, `SystemConfigService` self-invocation 수정
- FE 계약 문서 정합: `NotificationControllerDocs`의 옛 값 `SYSTEM_NOTICE` → `ANNOUNCEMENT`, 미구현 `itemCustomTags` 문서 정리
- 규칙 문서: AGENTS.md를 CLAUDE.md 참조로(사본 제거), 다운로드 GET 예외 명시
- 건드린 파일의 배너·변경경위 주석 정리 (그 외 파일 주석은 손대지 않음)

## D — 대형 파일 분해 (동작 불변)
1. `AdminApiController`(1752) → 도메인별 Admin 컨트롤러 + `*ControllerDocs` (URL 불변)
2. `AdminMemberService`(1005) → 정지/탈퇴/조회 책임별 분리
3. `ChatActionRecommendationService`(930) → 규칙 판정·프롬프트·쿨다운·설정 해석 분리
4. `ItemService`(914) → 관리자 로직은 Application으로, 조회/좋아요/등록 분리
5. `TradeControllerDocs`(1431) → 예시 JSON 상수 분리 검토
- 파일 1개 = 커밋 1개, 목표 500줄 이하, URL·응답 불변

## E — 아키텍처 규칙 테스트·위반 동결
- `install-tests --stack spring` → `ArchitectureRulesTest` + `docs/architecture/rules/*.rule`
- 규칙: controller-must-not-import-repository, no-raw-error-map, no-new-object-mapper, no-response-status-exception, no-token-in-log
- A~D 이후 남은 위반만 동결(`*.allow`), 새 위반은 테스트 실패

## FE 쪽 (별도 FE 이슈 제안, 이번 BE 범위 밖)
- 알림 타입 `SYSTEM_NOTICE` → `ANNOUNCEMENT` 맞춤, 카테고리 4개 추가, `errorData['code']` → `errorCode`, 채팅 추천·STOMP 에러 큐 구독, 웹소켓 주소 환경 분리

## 진행 순서
A(즉시, 보안) → B → C → D → E. C를 D 앞에 두는 이유: 사본·죽은 코드를 먼저 줄이면 분해 대상이 작아진다. E는 마지막에 남은 위반을 동결.
