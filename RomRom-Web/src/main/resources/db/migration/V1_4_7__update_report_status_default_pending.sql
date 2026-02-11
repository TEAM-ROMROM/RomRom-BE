-- 신고(Report) 테이블 status 컬럼 NULL 데이터 PENDING으로 업데이트
-- V1.4.7: 신고 관리 기능 추가에 따라 기존 NULL status를 PENDING으로 초기화

DO $$
BEGIN
    -- item_report 테이블이 존재하면 status NULL → PENDING
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'item_report'
    ) THEN
        UPDATE item_report SET status = 'PENDING' WHERE status IS NULL;
        RAISE NOTICE 'item_report: status NULL → PENDING 업데이트 완료';
    ELSE
        RAISE NOTICE 'item_report 테이블이 존재하지 않습니다.';
    END IF;

    -- member_report 테이블이 존재하면 status NULL → PENDING
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'member_report'
    ) THEN
        UPDATE member_report SET status = 'PENDING' WHERE status IS NULL;
        RAISE NOTICE 'member_report: status NULL → PENDING 업데이트 완료';
    ELSE
        RAISE NOTICE 'member_report 테이블이 존재하지 않습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '신고 status 업데이트 중 오류 발생: %', SQLERRM;
END $$;
