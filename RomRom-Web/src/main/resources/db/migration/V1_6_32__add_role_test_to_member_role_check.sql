DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) THEN
        -- 기존 role 체크 제약 조건 제거
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'member'::regclass
            AND conname = 'member_role_check'
        ) THEN
            ALTER TABLE member DROP CONSTRAINT member_role_check;
        END IF;

        -- ROLE_TEST 포함한 제약 조건 추가
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'member'::regclass
            AND conname = 'member_role_check'
        ) THEN
            ALTER TABLE member ADD CONSTRAINT member_role_check
                CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN', 'ROLE_TEST'));
        END IF;
    ELSE
        RAISE NOTICE 'member 테이블이 존재하지 않아 제약 조건을 수정하지 않습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
