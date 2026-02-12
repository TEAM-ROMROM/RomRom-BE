# RomRom-BE 프로젝트 컨벤션

## 전체 API 컨벤션

### Response 원칙
- 프로젝트 전체적으로 Entity 객체를 DTO로 변환하지 않고 DB 값 그대로 Response에 담아서 전송
- 별도의 data class 변환 없이 JPA Entity를 직접 응답에 포함하는 것이 기본 원칙
- 일부 예외 케이스를 제외하면 Entity → DTO 매핑 없이 직접 반환

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
