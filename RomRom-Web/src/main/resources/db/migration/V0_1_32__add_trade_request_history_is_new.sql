-- is_new 컬럼 추가
-- V0.1.32: TradeRequestHistory에 is_new 필드 추가
DO $$
BEGIN
    -- 1. trade_request_history 테이블 존재 여부 확인
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'trade_request_history'
    ) THEN
        -- 2. is_new 컬럼이 없으면 추가
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public'
            AND table_name = 'trade_request_history' 
            AND column_name = 'is_new'
        ) THEN
            ALTER TABLE public.trade_request_history
                ADD COLUMN is_new boolean DEFAULT TRUE;
            RAISE NOTICE 'trade_request_history 테이블에 is_new 컬럼을 추가했습니다';
        ELSE
            RAISE NOTICE 'is_new 컬럼이 이미 trade_request_history 테이블에 존재합니다';
        END IF;
        
        -- 3. 기존 데이터 백필 (is_new가 NULL인 경우)
        UPDATE public.trade_request_history
        SET is_new = TRUE
        WHERE is_new IS NULL;
        
        -- 4. NOT NULL 제약조건 추가 (컬럼이 NULL을 허용하지 않도록)
        -- 먼저 기존 제약조건이 있는지 확인
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public'
            AND table_name = 'trade_request_history' 
            AND column_name = 'is_new'
            AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE public.trade_request_history
                ALTER COLUMN is_new SET NOT NULL;
            RAISE NOTICE 'trade_request_history.is_new 컬럼에 NOT NULL 제약조건을 추가했습니다';
        ELSE
            RAISE NOTICE 'trade_request_history.is_new 컬럼은 이미 NOT NULL 제약조건이 있습니다';
        END IF;
    ELSE
        RAISE NOTICE 'trade_request_history 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
        -- 오류가 발생해도 계속 진행 (Flyway가 실패로 처리하지 않음)
END $$;