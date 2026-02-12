-- item_report, member_report 테이블에 status 컬럼 추가
-- V1.4.9: ddl-auto:update가 컬럼을 생성하지 못한 경우를 대비한 명시적 컬럼 추가

DO $$
BEGIN
    -- item_report 테이블에 status 컬럼 추가
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'item_report'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'item_report' AND column_name = 'status'
    ) THEN
        ALTER TABLE item_report ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT 'PENDING';
        RAISE NOTICE 'item_report: status 컬럼 추가 완료';
    ELSE
        RAISE NOTICE 'item_report: status 컬럼이 이미 존재하거나 테이블이 없습니다.';
    END IF;

    -- member_report 테이블에 status 컬럼 추가
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'member_report'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'member_report' AND column_name = 'status'
    ) THEN
        ALTER TABLE member_report ADD COLUMN status VARCHAR(255) NOT NULL DEFAULT 'PENDING';
        RAISE NOTICE 'member_report: status 컬럼 추가 완료';
    ELSE
        RAISE NOTICE 'member_report: status 컬럼이 이미 존재하거나 테이블이 없습니다.';
    END IF;

    -- 기존 NULL 데이터 PENDING으로 업데이트 (V1_4_7 실패 대비)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'item_report'
    ) THEN
        UPDATE item_report SET status = 'PENDING' WHERE status IS NULL;
        RAISE NOTICE 'item_report: NULL status → PENDING 업데이트 완료';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'member_report'
    ) THEN
        UPDATE member_report SET status = 'PENDING' WHERE status IS NULL;
        RAISE NOTICE 'member_report: NULL status → PENDING 업데이트 완료';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'report status 컬럼 추가 중 오류 발생: %', SQLERRM;
END $$;
