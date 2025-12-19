-- TradeRequestHistory trade_status 체크 제약조건 확장
-- V1.0.1: 채팅 상태 포함하도록 제약조건 확장
DO $$
BEGIN
    -- 1. trade_request_history 테이블 존재 여부 확인
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'trade_request_history'
    ) THEN
        -- 2. 기존 제약조건 삭제 (존재하는 경우)
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE table_schema = 'public'
            AND constraint_name = 'trade_request_history_trade_status_check'
            AND table_name = 'trade_request_history'
        ) THEN
            ALTER TABLE public.trade_request_history
                DROP CONSTRAINT trade_request_history_trade_status_check;
            RAISE NOTICE '기존 trade_request_history_trade_status_check 제약조건을 삭제했습니다';
        END IF;
        
        -- 3. 새로운 제약조건 추가
        ALTER TABLE public.trade_request_history
            ADD CONSTRAINT trade_request_history_trade_status_check
                CHECK ( (trade_status >= 0) AND (trade_status <= 3) );
        RAISE NOTICE 'trade_request_history_trade_status_check 제약조건을 추가했습니다 (0~3 범위)';
    ELSE
        RAISE NOTICE 'trade_request_history 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
        -- 오류가 발생해도 계속 진행 (Flyway가 실패로 처리하지 않음)
END $$;