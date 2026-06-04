-- 거래 후기(trade_review) 관리자 블라인드 처리 컬럼 추가
-- #771: BlindInfo(@Embeddable) - isBlinded/blindReason/blindByAdminId/blindDate
-- V1_4_60(item admin hidden) 동일 패턴: 컬럼별 테이블 존재 + 컬럼 부재 가드 + 멱등 블록

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'is_blinded'
    ) THEN
        ALTER TABLE trade_review ADD COLUMN is_blinded BOOLEAN NOT NULL DEFAULT false;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_reason'
    ) THEN
        ALTER TABLE trade_review ADD COLUMN blind_reason VARCHAR(500);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_by_admin_id'
    ) THEN
        ALTER TABLE trade_review ADD COLUMN blind_by_admin_id UUID;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'trade_review'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'trade_review' AND column_name = 'blind_date'
    ) THEN
        ALTER TABLE trade_review ADD COLUMN blind_date TIMESTAMP;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
