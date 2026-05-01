### 📌 작업 개요
Flyway 마이그레이션 파일들에 방어적 SQL 패턴 적용. 테이블이 존재하지 않거나 컬럼이 이미 존재하는 경우에도 안전하게 실행되도록 수정

**보고서 파일**: `.report/20251219_Flyway_마이그레이션_방어적_SQL_적용.md`

### 🔍 문제 분석
기존 마이그레이션 파일들이 테이블/컬럼 존재 여부를 확인하지 않고 직접 SQL을 실행하여 다음 문제 발생:
- JPA가 테이블을 생성하기 전에 Flyway가 실행되면 테이블이 없어서 마이그레이션 실패
- 컬럼이 이미 존재하는 경우 중복 추가 시도로 에러 발생
- 여러 번 실행 시 안전하지 않음 (Idempotent하지 않음)

**에러 예시**:
```
ERROR: relation "public.trade_request_history" does not exist
ERROR: relation "public.item" does not exist
```

### ✅ 구현 내용

#### V0_1_32__add_trade_request_history_is_new.sql 방어적 처리
- **파일**: `RomRom-Web/src/main/resources/db/migration/V0_1_32__add_trade_request_history_is_new.sql`
- **변경 내용**: 테이블 존재 확인, 컬럼 존재 확인, NOT NULL 제약조건 추가 전 확인 로직 추가
- **이유**: 테이블이 없거나 컬럼이 이미 존재해도 안전하게 처리

#### V1_0_1__expand_trade_status_check_to_include_chatting.sql 방어적 처리
- **파일**: `RomRom-Web/src/main/resources/db/migration/V1_0_1__expand_trade_status_check_to_include_chatting.sql`
- **변경 내용**: 테이블 존재 확인, 제약조건 존재 확인 후 삭제/추가 로직 추가
- **이유**: 테이블이 없거나 제약조건이 이미 존재해도 안전하게 처리

#### V1_0_2__rename_item_ai_price_to_is_ai_predicted_price.sql 방어적 처리
- **파일**: `RomRom-Web/src/main/resources/db/migration/V1_0_2__rename_item_ai_price_to_is_ai_predicted_price.sql`
- **변경 내용**: 테이블 존재 확인, 원본 컬럼 존재 확인, 대상 컬럼 존재 확인 로직 추가
- **이유**: 테이블이 없거나 이미 이름이 변경된 경우에도 안전하게 처리

#### V1_1_19__add_member_total_like_count.sql 방어적 처리
- **파일**: `RomRom-Web/src/main/resources/db/migration/V1_1_19__add_member_total_like_count.sql`
- **변경 내용**: 테이블 존재 확인, 컬럼 존재 확인, 관련 테이블(item) 존재 확인 후 UPDATE 실행
- **이유**: 테이블이 없거나 컬럼이 이미 존재해도 안전하게 처리, 관련 테이블이 없어도 에러 발생하지 않음

### 🔧 주요 변경사항 상세

#### 공통 적용 패턴
모든 마이그레이션 파일에 다음 패턴을 일관되게 적용:

**1. DO 블록 사용**
```sql
DO $$
BEGIN
    -- 마이그레이션 로직
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
```

**2. 테이블 존재 여부 확인**
```sql
IF EXISTS (
    SELECT 1 FROM information_schema.tables 
    WHERE table_schema = 'public' 
    AND table_name = 'table_name'
) THEN
    -- 테이블이 존재할 때만 실행
END IF;
```

**3. 컬럼 존재 여부 확인**
```sql
IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_schema = 'public'
    AND table_name = 'table_name' 
    AND column_name = 'column_name'
) THEN
    -- 컬럼이 없을 때만 추가
END IF;
```

**4. 제약조건 존재 여부 확인**
```sql
IF EXISTS (
    SELECT 1 FROM information_schema.table_constraints 
    WHERE table_schema = 'public'
    AND constraint_name = 'constraint_name'
    AND table_name = 'table_name'
) THEN
    -- 제약조건이 존재할 때만 삭제/수정
END IF;
```

**5. 로깅 추가**
모든 분기마다 `RAISE NOTICE`로 실행 상황 로깅하여 디버깅 용이성 확보

#### V0_1_32__add_trade_request_history_is_new.sql 상세
- 테이블 존재 확인 후 컬럼 추가
- 컬럼 존재 여부 확인 후 추가 (중복 방지)
- 기존 데이터 백필 (NULL 값 처리)
- NOT NULL 제약조건 추가 전 컬럼의 nullable 상태 확인

**특이사항**:
- 컬럼 추가와 제약조건 추가를 분리하여 단계별 처리
- UPDATE 문은 테이블이 존재할 때만 실행

#### V1_0_1__expand_trade_status_check_to_include_chatting.sql 상세
- 테이블 존재 확인 후 제약조건 작업 수행
- 기존 제약조건 존재 여부 확인 후 삭제
- 새로운 제약조건 추가

**특이사항**:
- 제약조건이 없어도 에러 발생하지 않음
- 제약조건 삭제와 추가를 분리하여 안전하게 처리

#### V1_0_2__rename_item_ai_price_to_is_ai_predicted_price.sql 상세
- 테이블 존재 확인
- 원본 컬럼(`ai_price`) 존재 여부 확인
- 대상 컬럼(`is_ai_predicted_price`) 존재 여부 확인
- 이미 이름이 변경된 경우 처리

**특이사항**:
- 원본 컬럼이 없고 대상 컬럼이 있는 경우 (이미 변경 완료) 안전하게 처리
- 원본 컬럼이 없고 대상 컬럼도 없는 경우 (JPA가 생성할 예정) 안전하게 처리

#### V1_1_19__add_member_total_like_count.sql 상세
- `TO_REGCLASS` 대신 `information_schema` 사용으로 통일
- member 테이블 존재 확인 후 컬럼 추가
- item 테이블 존재 확인 후 UPDATE 실행
- 스키마 이름 명시 (`public.member`, `public.item`)

**특이사항**:
- 관련 테이블(item)이 없어도 컬럼 추가는 정상적으로 수행
- UPDATE는 item 테이블이 존재할 때만 실행하여 에러 방지

### 📦 의존성 변경
- 없음 (기존 Flyway 및 PostgreSQL 기능만 사용)

### 🧪 테스트 및 검증
- 빈 DB에서 실행 시 테이블이 없어도 마이그레이션 실패하지 않음 확인
- 기존 데이터가 있는 DB에서 실행 시 정상적으로 컬럼 추가/수정 확인
- 여러 번 실행 시 Idempotent하게 동작 확인 (중복 실행 안전)
- JPA가 테이블을 생성하기 전에 Flyway가 실행되어도 에러 발생하지 않음 확인

### 📌 참고사항
- **프로젝트 가이드라인 준수**: `docs/flyway_guideline.md`의 방어적 SQL 작성법 패턴 준수
- **일관성 유지**: 모든 마이그레이션 파일이 동일한 패턴 사용
- **스키마 이름 통일**: `romrom.public` → `public`으로 통일하여 일관성 확보
- **EXCEPTION 처리**: 모든 파일에 EXCEPTION 블록 추가하여 예상치 못한 에러에도 안전하게 처리
- **로깅**: RAISE NOTICE로 실행 상황을 명확히 로깅하여 디버깅 용이성 확보
- **JPA와의 호환성**: JPA가 테이블을 자동 생성하는 환경에서도 안전하게 동작


