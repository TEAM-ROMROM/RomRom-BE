-- notification_history 테이블의 payload 컬럼을 VARCHAR(255) -> TEXT로 변경
-- 알림 payload(JSON)가 255자를 초과할 수 있어 TEXT 타입으로 변경

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'notification_history'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'notification_history' AND column_name = 'payload'
          AND data_type = 'character varying'
    ) THEN
        ALTER TABLE notification_history ALTER COLUMN payload TYPE TEXT;
        RAISE NOTICE 'notification_history: payload 컬럼 VARCHAR(255) → TEXT 변경 완료';
    ELSE
        RAISE NOTICE 'notification_history: payload 컬럼이 이미 TEXT이거나 테이블이 없습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'notification_history payload 컬럼 변경 중 오류 발생: %', SQLERRM;
END $$;
