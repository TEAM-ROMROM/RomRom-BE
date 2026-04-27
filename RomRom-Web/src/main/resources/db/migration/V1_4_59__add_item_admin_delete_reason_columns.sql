DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'admin_delete_reason'
    ) THEN
        ALTER TABLE item ADD COLUMN admin_delete_reason VARCHAR(50);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'admin_delete_detail'
    ) THEN
        ALTER TABLE item ADD COLUMN admin_delete_detail VARCHAR(500);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
