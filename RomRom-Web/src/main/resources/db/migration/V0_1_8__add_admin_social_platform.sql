-- Admin 소셜 플랫폼 enum 추가
-- V0.1.8: Admin 계정 관리 시스템 지원
DO $$
BEGIN
    -- 1. 기존 체크 제약조건 삭제
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'member_social_platform_check'
    ) THEN
        ALTER TABLE member DROP CONSTRAINT member_social_platform_check;
        RAISE NOTICE '기존 체크 제약조건을 삭제했습니다: member_social_platform_check';
    END IF;
    
    -- 2. social_platform_enum 타입이 존재하는지 확인
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'social_platform_enum') THEN
        -- enum 타입이 없으면 생성
        CREATE TYPE social_platform_enum AS ENUM ('NORMAL', 'KAKAO', 'GOOGLE', 'ADMIN');
        RAISE NOTICE 'social_platform_enum 타입을 생성했습니다';
    ELSE
        -- enum 타입이 있으면 ADMIN 값만 안전하게 추가
        IF NOT EXISTS (
            SELECT 1 FROM pg_enum 
            WHERE enumlabel = 'ADMIN' 
            AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'social_platform_enum')
        ) THEN
            ALTER TYPE social_platform_enum ADD VALUE 'ADMIN';
            RAISE NOTICE 'social_platform_enum에 ADMIN 값을 추가했습니다';
        ELSE
            RAISE NOTICE 'ADMIN 값이 이미 social_platform_enum에 존재합니다';
        END IF;
    END IF;
    
    -- 3. 새로운 체크 제약조건 추가
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'member') THEN
        ALTER TABLE member ADD CONSTRAINT member_social_platform_check 
            CHECK (social_platform IN ('NORMAL', 'KAKAO', 'GOOGLE', 'ADMIN'));
        RAISE NOTICE 'ADMIN 값을 포함한 새로운 체크 제약조건을 추가했습니다';
    END IF;
    
    -- 4. member 테이블이 존재하고 password 컬럼이 없으면 추가
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'member') THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'member' AND column_name = 'password'
        ) THEN
            ALTER TABLE member ADD COLUMN password VARCHAR(255);
            RAISE NOTICE 'member 테이블에 password 컬럼을 추가했습니다';
        ELSE
            RAISE NOTICE 'password 컬럼이 이미 member 테이블에 존재합니다';
        END IF;
    ELSE
        RAISE NOTICE 'member 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
        -- 오류가 발생해도 계속 진행 (Flyway가 실패로 처리하지 않음)
END $$;
