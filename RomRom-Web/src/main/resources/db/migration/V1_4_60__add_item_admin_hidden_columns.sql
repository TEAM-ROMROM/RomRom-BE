DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'is_admin_hidden'
    ) THEN
        ALTER TABLE item ADD COLUMN is_admin_hidden BOOLEAN NOT NULL DEFAULT false;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'admin_hide_reason'
    ) THEN
        ALTER TABLE item ADD COLUMN admin_hide_reason VARCHAR(500);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
