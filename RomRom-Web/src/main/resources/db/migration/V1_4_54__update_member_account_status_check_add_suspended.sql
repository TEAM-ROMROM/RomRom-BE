DO $$
BEGIN
    -- member 테이블의 account_status 체크 제약 조건에 SUSPENDED_ACCOUNT 추가
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) THEN
        -- 기존 제약 조건이 있으면 삭제
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'member'::regclass
            AND conname = 'member_account_status_check'
        ) THEN
            ALTER TABLE member DROP CONSTRAINT member_account_status_check;
        END IF;

        -- SUSPENDED_ACCOUNT 포함한 제약 조건이 없으면 추가
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'member'::regclass
            AND conname = 'member_account_status_check'
        ) THEN
            ALTER TABLE member ADD CONSTRAINT member_account_status_check
                CHECK (account_status IN ('ACTIVE_ACCOUNT', 'DELETE_ACCOUNT', 'TEST_ACCOUNT', 'SUSPENDED_ACCOUNT'));
        END IF;
    ELSE
        RAISE NOTICE 'member 테이블이 존재하지 않아 제약 조건을 수정하지 않습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
