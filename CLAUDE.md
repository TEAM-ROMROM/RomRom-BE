# Git 커밋 금지

- **에이전트는 절대로 git commit을 실행하지 않는다**
- `git add`, `git commit`, `git push` 등 git 변경 명령어를 자동으로 실행하지 말 것
- 커밋은 반드시 사용자가 직접 수행한다

# RomRom-BE 프로젝트 컨벤션

## 전체 API 컨벤션

### Request/Response 구조 원칙
- **하나의 Controller에 하나의 Request, 하나의 Response**로 관리
- 어쩔 수 없는 예외 상황을 제외하면 Controller마다 전용 Request/Response 클래스 사용
- 예: `AdminApiController` → `AdminRequest`, `AdminResponse`

### POST API 패턴 (전체 공통)
- **모든 API는 POST + `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` + `@ModelAttribute` 패턴 사용**
- 예시:
  ```java
  @PostMapping(value = "/sign-in", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @LogMonitor
  public ResponseEntity<AuthResponse> signIn(@ModelAttribute AuthRequest request) {
      return ResponseEntity.ok(authService.signIn(request));
  }
  ```

### Response 원칙
- 프로젝트 전체적으로 Entity 객체를 DTO로 변환하지 않고 DB 값 그대로 Response에 담아서 전송
- 별도의 data class 변환 없이 JPA Entity를 직접 응답에 포함하는 것이 기본 원칙
- 일부 예외 케이스를 제외하면 Entity → DTO 매핑 없이 직접 반환

## API 문서화 컨벤션 (Swagger / @ApiChangeLog)

### 구조 원칙
- **각 Controller마다 전용 `*ControllerDocs` 인터페이스를 생성**하고, Controller는 이 인터페이스를 `implements`
- Swagger 어노테이션(`@Operation`, `@Tag`, `@ApiChangeLogs`)은 **인터페이스에만** 작성, 구현체(Controller)는 깔끔하게 유지
- 파일 위치: Controller와 **동일한 패키지**에 `{ControllerName}Docs.java`로 생성

### @ApiChangeLog 작성 규칙
- 라이브러리: `me.suhsaechan.suhapilog.annotation.ApiChangeLog` / `ApiChangeLogs`
- **날짜 형식**: `"YYYY.MM.DD"` (예: `"2026.02.27"`)
- **author**: `Author.SUHSAECHAN` 등 `com.romrom.common.dto.Author` 상수 사용
- **issueNumber**: GitHub Issue 번호 (int)
- **description**: 변경 내용 한 줄 설명
- 예시:
  ```java
  @ApiChangeLogs({
      @ApiChangeLog(date = "2026.02.27", author = Author.SUHSAECHAN, issueNumber = 552, description = "관리자용 물품 단건 조회 API 추가"),
  })
  ```

### @Operation 작성 규칙
- **summary**: API 한 줄 요약
- **description**: 아래 마크다운 포맷 준수
  ```
  ## 인증(JWT): **필요/불필요**

  ## 요청 파라미터 (DTO명)
  - **`fieldName`**: 설명

  ## 반환값 (DTO명)
  - **`fieldName`**: 설명

  ## 에러코드
  - **`ERROR_CODE`**: 설명
  ```

### Author 상수 목록 (com.romrom.common.dto.Author)
- `Author.SUHSAECHAN` = "서새찬"
- `Author.BAEKJIHOON` = "백지훈"
- `Author.WISEUNGJAE` = "위승재"
- `Author.KIMNAYOUNG` = "김나영"
- `Author.KIMKYUSEOP` = "김규섭"

## Admin API 컨벤션

### DTO 네이밍
- Admin 관련 Controller는 하나의 Request와 하나의 Response로 관리
- 네이밍: `Admin{도메인}Request`, `Admin{도메인}Response`
- 예: `AdminReportRequest`, `AdminReportResponse`

### Response 구조
- 전체 원칙과 동일하게 Entity 객체를 그대로 Response에 포함
- 목록 조회 시 Page 정보(totalPages, totalElements, currentPage)는 Response에 포함

### Action 기반 API 패턴
- Admin API는 단일 엔드포인트에 `action` 파라미터로 동작을 구분
- 예: `POST /admin/api/reports` → action: `item-list`, `member-list`, `update-status` 등

## Flyway 마이그레이션 컨벤션

### 필수 규칙: 테이블 존재 여부 체크
- **모든 SQL문은 반드시 테이블 존재 여부를 확인한 후 실행해야 한다**
- `ALTER TABLE`, `UPDATE`, `INSERT`, `DELETE` 등 테이블을 대상으로 하는 모든 SQL문은 `IF EXISTS` 체크로 감싸야 한다
- 테이블이 존재하지 않을 경우 `RAISE NOTICE`로 알림 처리

### 마이그레이션 파일 구조
- 경로: `RomRom-Web/src/main/resources/db/migration/`
- 네이밍: `V{버전}__설명.sql` (예: `V1_4_9__add_report_status_column.sql`)
- 모든 마이그레이션은 `DO $$ BEGIN ... EXCEPTION WHEN OTHERS THEN RAISE WARNING ... END $$;` 블록으로 감싸서 멱등성 보장
- 기존 마이그레이션 파일들을 참고하여 동일한 패턴 준수

### 예시 패턴
```sql
DO $$
BEGIN
    -- 컬럼 추가
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = '테이블명'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = '테이블명' AND column_name = '컬럼명'
    ) THEN
        ALTER TABLE 테이블명 ADD COLUMN 컬럼명 타입;
    END IF;

    -- 데이터 업데이트
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = '테이블명'
    ) THEN
        UPDATE 테이블명 SET ...;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
```
