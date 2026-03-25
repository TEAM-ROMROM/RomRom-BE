DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'suspend_reason'
    ) THEN
        ALTER TABLE member ADD COLUMN suspend_reason VARCHAR(255);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'suspended_at'
    ) THEN
        ALTER TABLE member ADD COLUMN suspended_at TIMESTAMP;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'suspended_until'
    ) THEN
        ALTER TABLE member ADD COLUMN suspended_until TIMESTAMP;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
