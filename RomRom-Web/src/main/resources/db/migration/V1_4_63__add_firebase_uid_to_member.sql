DO $$
BEGIN
    -- firebase_uid 컬럼 추가 (카카오 Custom Token 방식 로그인 시 Firebase UID 저장)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'member' AND column_name = 'firebase_uid'
    ) THEN
        ALTER TABLE member ADD COLUMN firebase_uid VARCHAR(255);

        -- unique 제약 조건 추가 (null 제외, PostgreSQL에서 null은 unique 제약 미적용)
        ALTER TABLE member ADD CONSTRAINT uq_member_firebase_uid UNIQUE (firebase_uid);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_63 마이그레이션 오류: %', SQLERRM;
END $$;
