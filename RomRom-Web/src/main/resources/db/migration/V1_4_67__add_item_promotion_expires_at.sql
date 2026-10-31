-- 롬업(우선 노출) 만료 일시 컬럼 + 활성 프로모션 조회 인덱스 (#819)
-- 신규 테이블 item_promotion_history, home_popup(#749)은 JPA ddl-auto update로 생성 (Announcement와 동일)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'promotion_expires_at'
    ) THEN
        ALTER TABLE item ADD COLUMN promotion_expires_at TIMESTAMP(6);
        RAISE NOTICE 'item.promotion_expires_at 컬럼이 추가되었습니다.';
    END IF;

    -- 홈 피드 프로모션 후보 조회용 (만료 전 물품만 소수 > 부분 인덱스)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'promotion_expires_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'item' AND indexname = 'idx_item_promotion_expires_at'
    ) THEN
        CREATE INDEX idx_item_promotion_expires_at ON item (promotion_expires_at)
            WHERE promotion_expires_at IS NOT NULL;
        RAISE NOTICE 'idx_item_promotion_expires_at 인덱스가 생성되었습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'item 롬업 컬럼 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
