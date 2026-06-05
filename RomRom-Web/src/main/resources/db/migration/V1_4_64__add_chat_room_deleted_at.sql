DO $$
BEGIN
    -- deleted_at 컬럼 추가 (즉시 물리삭제 → soft delete + 배치 아카이브 전환, #750)
    -- null: 활성 방, non-null: 배치 청소 대기 상태
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_room'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'chat_room' AND column_name = 'deleted_at'
    ) THEN
        ALTER TABLE chat_room ADD COLUMN deleted_at TIMESTAMP;

        -- soft-delete된 방만 인덱싱 (활성 방은 deleted_at IS NULL로 대다수 → partial index가 공간/성능 우수)
        -- 배치 청소 쿼리(WHERE deleted_at IS NOT NULL AND deleted_at < threshold) 패턴에 최적
        CREATE INDEX IF NOT EXISTS idx_chat_room_deleted_at ON chat_room (deleted_at) WHERE deleted_at IS NOT NULL;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_64 마이그레이션 오류: %', SQLERRM;
END $$;
