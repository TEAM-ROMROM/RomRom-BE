# Flyway Database Migration 가이드라인

> RomRom-BE 프로젝트 데이터베이스 마이그레이션 관리 가이드

---

## 📋 목차
1. [Flyway란?](#flyway란)
2. [프로젝트 설정](#프로젝트-설정)
3. [마이그레이션 파일 작성 규칙](#마이그레이션-파일-작성-규칙)
4. [방어적 SQL 작성법](#방어적-sql-작성법)
5. [실무 예시](#실무-예시)
6. [트러블슈팅](#트러블슈팅)
7. [베스트 프랙티스](#베스트-프랙티스)

---

## Flyway란?

**Flyway**는 데이터베이스 스키마를 버전 관리하는 도구입니다. Git이 소스코드를 관리하듯이, Flyway는 데이터베이스 변경사항을 체계적으로 관리합니다.

### 주요 특징
- 📁 SQL 파일로 스키마 변경 관리
- 🔄 자동 마이그레이션 실행
- 📊 `flyway_schema_history` 테이블에 실행 이력 저장
- 🛡️ 한 번 실행된 마이그레이션은 재실행 방지

---

## 프로젝트 설정

### 1. 의존성 설정 (build.gradle)
```gradle
// Database
implementation 'org.postgresql:postgresql'

// Flyway Database Migration
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

### 2. application.yml 설정
```yaml
spring:
  # Flyway Database Migration 설정
  flyway:
    enabled: true
    baseline-on-migrate: true # 기존 DB에 Flyway 처음 적용할 때 필요 (계속 true로 해놔도 문제 없습니다)
    locations: classpath:db/migration
    sql-migration-suffixes: .sql
    validate-on-migrate: true
    out-of-order: false
```

### 3. 디렉토리 구조
```
RomRom-Web/src/main/resources/
└── db/
    └── migration/
        ├── V0_1_8__add_admin_social_platform.sql
        ├── V0_1_9__add_notification_table.sql
        └── V0_2_0__update_member_constraints.sql
```

---

## 마이그레이션 파일 작성 규칙

### 파일명 규칙 (엄격)
```
V{버전}__{설명}.sql

예시:
✅ V0_1_8__add_admin_social_platform.sql
✅ V0_2_0__create_notification_table.sql
✅ V1_0_0__add_user_preferences.sql

❌ v0_1_8__example.sql          (소문자 v)
❌ V0.1.8__example.sql          (점 사용)
❌ V0_1_8_example.sql           (언더스코어 1개)
❌ V0_1_8__example              (.sql 없음)
```

### 버전 체계 ⚠️ 중요!

**⚠️ 마이그레이션 파일 생성 전 반드시 확인:**
1. `git checkout main && git pull origin main` 실행
2. `build.gradle`에서 현재 버전 확인
3. 해당 버전으로 마이그레이션 파일명 결정

```bash
# 1단계: 최신 main 브랜치로 업데이트
git checkout main
git pull origin main

# 2단계: 현재 프로젝트 버전 확인
grep "version = " build.gradle
# 출력 예시: version = '0.1.8'

# 3단계: 해당 버전으로 마이그레이션 파일 생성
# V0_1_8__your_feature_description.sql
```

**버전 형식:**
```
프로젝트 버전: 0.1.8 → Flyway 파일: V0_1_8__설명.sql

V{메이저}_{마이너}_{패치}__{설명}.sql
│    │       │       │
│    │       │       └─ 기능 설명 (영문, 언더스코어 2개)
│    │       └───────── 패치 버전
│    └──────────────── 마이너 버전
└───────────────────── 메이저 버전 (V는 Version)
```

**❌ 잘못된 버전 사용 시 문제점:**
- Flyway 실행 순서 꼬임
- 다른 개발자와의 버전 충돌
- 운영 배포 시 마이그레이션 누락

---

## 방어적 SQL 작성법

### 핵심 원칙: "있으면 놔두고, 없으면 만든다"

모든 마이그레이션은 **여러 번 실행해도 안전**해야 합니다.

### 1. 테이블 생성
```sql
-- 방어적 테이블 생성
CREATE TABLE IF NOT EXISTS notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    content TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 2. 컬럼 추가
```sql
DO $$
BEGIN
    -- password 컬럼이 없으면 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'member' AND column_name = 'password'
    ) THEN
        ALTER TABLE member ADD COLUMN password VARCHAR(255);
        RAISE NOTICE '컬럼 password가 member 테이블에 추가되었습니다.';
    ELSE
        RAISE NOTICE '컬럼 password가 이미 존재합니다.';
    END IF;
END $$;
```

### 3. 인덱스 생성
```sql
DO $$
BEGIN
    -- 인덱스가 없으면 생성
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes 
        WHERE tablename = 'member' AND indexname = 'idx_member_email'
    ) THEN
        CREATE INDEX idx_member_email ON member(email);
        RAISE NOTICE '인덱스 idx_member_email이 생성되었습니다.';
    ELSE
        RAISE NOTICE '인덱스 idx_member_email이 이미 존재합니다.';
    END IF;
END $$;
```

### 4. ENUM 타입 관리
```sql
DO $$
BEGIN
    -- enum 타입이 없으면 생성
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notification_type_enum') THEN
        CREATE TYPE notification_type_enum AS ENUM ('SYSTEM', 'USER', 'ADMIN');
        RAISE NOTICE 'notification_type_enum 타입이 생성되었습니다.';
    END IF;

    -- ADMIN 값이 없으면 추가
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum 
        WHERE enumlabel = 'ADMIN' 
        AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'notification_type_enum')
    ) THEN
        ALTER TYPE notification_type_enum ADD VALUE 'ADMIN';
        RAISE NOTICE 'ADMIN 값이 notification_type_enum에 추가되었습니다.';
    END IF;

EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
```

### 5. 제약조건 추가
```sql
DO $$
BEGIN
    -- 제약조건이 없으면 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'member_email_unique' 
        AND table_name = 'member'
    ) THEN
        ALTER TABLE member ADD CONSTRAINT member_email_unique UNIQUE (email);
        RAISE NOTICE '제약조건 member_email_unique이 추가되었습니다.';
    ELSE
        RAISE NOTICE '제약조건 member_email_unique이 이미 존재합니다.';
    END IF;
END $$;
```

---

## 실무 예시

### 예시 1: 새 테이블 추가 (V0_1_9__create_notification_table.sql)
```sql
-- 알림 테이블 생성
-- V0.1.9: 알림 시스템 추가

DO $$
BEGIN
    -- 1. enum 타입 생성 (없으면)
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notification_type_enum') THEN
        CREATE TYPE notification_type_enum AS ENUM ('SYSTEM', 'USER', 'MARKETING');
        RAISE NOTICE 'notification_type_enum 타입이 생성되었습니다.';
    END IF;

    -- 2. 테이블 생성 (없으면)
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'notification') THEN
        CREATE TABLE notification (
            notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            member_id UUID NOT NULL,
            title VARCHAR(255) NOT NULL,
            content TEXT,
            notification_type notification_type_enum NOT NULL DEFAULT 'SYSTEM',
            is_read BOOLEAN NOT NULL DEFAULT FALSE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            read_at TIMESTAMP WITH TIME ZONE,
            
            -- 외래키 제약조건
            CONSTRAINT fk_notification_member 
                FOREIGN KEY (member_id) REFERENCES member(member_id) ON DELETE CASCADE
        );
        
        RAISE NOTICE 'notification 테이블이 생성되었습니다.';
    END IF;

    -- 3. 인덱스 생성 (없으면)
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_notification_member_id') THEN
        CREATE INDEX idx_notification_member_id ON notification(member_id);
        RAISE NOTICE '인덱스 idx_notification_member_id가 생성되었습니다.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_notification_created_at') THEN
        CREATE INDEX idx_notification_created_at ON notification(created_at DESC);
        RAISE NOTICE '인덱스 idx_notification_created_at가 생성되었습니다.';
    END IF;

EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '알림 테이블 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
```

### 예시 2: 컬럼 수정 (V0_2_0__update_member_constraints.sql)
```sql
-- 회원 테이블 제약조건 업데이트
-- V0.2.0: 회원 데이터 무결성 강화

DO $$
BEGIN
    -- 1. email 컬럼 UNIQUE 제약조건 추가
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'member_email_unique' AND table_name = 'member'
    ) THEN
        -- 중복 데이터 정리 (있다면)
        DELETE FROM member m1 USING member m2 
        WHERE m1.member_id < m2.member_id AND m1.email = m2.email;
        
        -- UNIQUE 제약조건 추가
        ALTER TABLE member ADD CONSTRAINT member_email_unique UNIQUE (email);
        RAISE NOTICE 'member 테이블에 email UNIQUE 제약조건이 추가되었습니다.';
    END IF;

    -- 2. phone_number 컬럼 추가 (없으면)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'member' AND column_name = 'phone_number'
    ) THEN
        ALTER TABLE member ADD COLUMN phone_number VARCHAR(20);
        RAISE NOTICE 'member 테이블에 phone_number 컬럼이 추가되었습니다.';
    END IF;

    -- 3. 기존 데이터 검증 및 업데이트
    UPDATE member SET phone_number = '미등록' WHERE phone_number IS NULL;
    RAISE NOTICE '기존 회원의 phone_number가 업데이트되었습니다.';

EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '회원 테이블 제약조건 업데이트 중 오류 발생: %', SQLERRM;
END $$;
```

---

## 트러블슈팅

### 자주 발생하는 문제들

#### 1. 마이그레이션 실패 시
```sql
-- flyway_schema_history에서 실패한 마이그레이션 확인
SELECT * FROM flyway_schema_history ORDER BY installed_on DESC LIMIT 5;

-- 실패한 마이그레이션 수동 수정 후 success로 업데이트
UPDATE flyway_schema_history 
SET success = true 
WHERE version = '0.1.8' AND success = false;
```

#### 2. enum 값 추가 실패
```sql
-- PostgreSQL에서 enum에 값 추가는 트랜잭션 밖에서만 가능
-- DO 블록 사용하지 말고 직접 실행
ALTER TYPE social_platform_enum ADD VALUE IF NOT EXISTS 'ADMIN';
```

#### 3. 개발환경별 차이 해결
```sql
-- 환경별 조건부 실행
DO $$
BEGIN
    -- 운영환경에서만 실행
    IF current_database() = 'romrom_prod' THEN
        -- 운영환경 전용 로직
        RAISE NOTICE '운영환경에서 실행됨';
    END IF;
END $$;
```

### 롤백 전략

#### Flyway는 기본적으로 Forward-only
- ❌ 자동 롤백 미지원
- ✅ 수동 롤백 스크립트 작성 필요

```sql
-- V0_1_8_1__rollback_admin_platform.sql (롤백용)
DO $$
BEGIN
    -- ADMIN enum 값 제거 (주의: 데이터 확인 후)
    IF EXISTS (
        SELECT 1 FROM pg_enum 
        WHERE enumlabel = 'ADMIN' 
        AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'social_platform_enum')
    ) THEN
        -- ADMIN을 사용하는 데이터가 있는지 확인
        IF NOT EXISTS (SELECT 1 FROM member WHERE social_platform = 'ADMIN') THEN
            -- 데이터가 없으면 enum 값 제거
            -- PostgreSQL 10+ 에서는 DROP VALUE 지원하지 않음
            RAISE WARNING 'ADMIN enum 값 제거는 수동으로 처리해야 합니다.';
        ELSE
            RAISE WARNING 'ADMIN을 사용하는 데이터가 존재하여 제거할 수 없습니다.';
        END IF;
    END IF;
END $$;
```

---

## 베스트 프랙티스

### 1. 명명 규칙
```
✅ 좋은 예시:
V0_1_8__add_admin_social_platform.sql
V0_1_9__create_notification_system.sql
V0_2_0__update_member_constraints.sql

❌ 나쁜 예시:
V1__test.sql                    (설명 부족)
V0_1_8__fix_bug.sql            (모호한 설명)
V0_1_8__add_😀_emoji.sql       (이모지 사용)
```

### 2. SQL 작성 원칙
- **방어적 작성**: 여러 번 실행해도 안전하게
- **명확한 로깅**: `RAISE NOTICE`로 실행 상황 알림
- **예외 처리**: `EXCEPTION` 블록으로 안전장치
- **주석 작성**: 왜 이 마이그레이션이 필요한지 설명

### 3. 테스트 원칙
```sql
-- 마이그레이션 작성 후 반드시 테스트
-- 1. 빈 DB에서 실행
-- 2. 기존 데이터가 있는 DB에서 실행  
-- 3. 두 번 연속 실행 (idempotent 확인)
```

### 4. 코드 리뷰 체크리스트
- [ ] 파일명이 규칙에 맞는가?
- [ ] DO 블록으로 방어적으로 작성했는가?
- [ ] RAISE NOTICE로 로깅이 충분한가?
- [ ] EXCEPTION 처리가 있는가?
- [ ] 기존 데이터에 영향이 없는가?
- [ ] 여러 번 실행해도 안전한가?

### 5. 마이그레이션 파일 생성 워크플로우
```bash
# ✅ 올바른 마이그레이션 파일 생성 과정
# 1. main 브랜치 최신 상태로 업데이트
git checkout main
git pull origin main

# 2. 현재 프로젝트 버전 확인
grep "version = " build.gradle
# 예시 출력: version = '0.1.8'

# 3. 기능 브랜치 생성
git checkout -b feature/add-notification-table

# 4. 올바른 버전으로 마이그레이션 파일 생성
touch RomRom-Web/src/main/resources/db/migration/V0_1_8__create_notification_table.sql

# 5. 마이그레이션 파일 작성 및 테스트
# 6. 커밋 및 PR 생성
```

### 6. 운영 배포 전 체크리스트
- [ ] **main pull 후 정확한 버전 확인 완료**
- [ ] 파일명이 현재 프로젝트 버전과 일치하는지 확인
- [ ] 로컬 환경에서 테스트 완료
- [ ] 테스트 DB에서 검증 완료
- [ ] 백업 계획 수립
- [ ] 롤백 계획 수립
- [ ] 팀원 코드 리뷰 완료

---

## 참고 자료

### 공식 문서
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)

### 프로젝트 관련 파일
- `RomRom-Web/build.gradle` - Flyway 의존성 설정
- `application.yml` - Flyway 설정
- `db/migration/` - 마이그레이션 파일들

### 유용한 SQL 쿼리
```sql
-- Flyway 실행 이력 확인
SELECT * FROM flyway_schema_history ORDER BY installed_on DESC;

-- 현재 DB의 모든 enum 타입 확인
SELECT t.typname, e.enumlabel 
FROM pg_type t 
JOIN pg_enum e ON t.oid = e.enumtypid 
ORDER BY t.typname, e.enumsortorder;

-- 테이블 컬럼 정보 확인
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_name = 'member';
```

---

## 📞 문의사항
마이그레이션 관련 문제가 발생하면:
1. 이 가이드라인 먼저 확인
2. `flyway_schema_history` 테이블 상태 확인
3. 팀 내 DB 담당자 문의 (서새찬, 백지훈)
4. 필요시 이 문서 업데이트

---
*마지막 업데이트: 2025-09-02*