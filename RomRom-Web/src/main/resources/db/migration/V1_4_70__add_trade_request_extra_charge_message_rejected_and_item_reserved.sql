-- 교환 요청 개선 (#859)
-- 1. trade_request_history: 추가금·한 줄 메시지·확인 시각·거절 시각 컬럼
-- 2. trade_status 체크 제약 0~5 확장 (REJECTED=5 추가, ORDINAL 저장)
--    PENDING=0, TRADED=1, CANCELED=2, CHATTING=3, TRADE_COMPLETE_REQUESTED=4, REJECTED=5
-- 3. item: 예약중 시작 일시 컬럼 + 예약 정리 스케줄러용 부분 인덱스
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'trade_request_history'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'trade_request_history' AND column_name = 'extra_charge_amount'
        ) THEN
            ALTER TABLE public.trade_request_history ADD COLUMN extra_charge_amount INTEGER;
            RAISE NOTICE 'trade_request_history.extra_charge_amount 컬럼을 추가했습니다.';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'trade_request_history' AND column_name = 'request_message'
        ) THEN
            ALTER TABLE public.trade_request_history ADD COLUMN request_message VARCHAR(100);
            RAISE NOTICE 'trade_request_history.request_message 컬럼을 추가했습니다.';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'trade_request_history' AND column_name = 'checked_at'
        ) THEN
            ALTER TABLE public.trade_request_history ADD COLUMN checked_at TIMESTAMP(6);
            RAISE NOTICE 'trade_request_history.checked_at 컬럼을 추가했습니다.';
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'trade_request_history' AND column_name = 'rejected_at'
        ) THEN
            ALTER TABLE public.trade_request_history ADD COLUMN rejected_at TIMESTAMP(6);
            RAISE NOTICE 'trade_request_history.rejected_at 컬럼을 추가했습니다.';
        END IF;

        -- trade_status 체크 제약 재생성 (0~5)
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = 'public'
              AND table_name = 'trade_request_history'
              AND constraint_name = 'trade_request_history_trade_status_check'
        ) THEN
            ALTER TABLE public.trade_request_history DROP CONSTRAINT trade_request_history_trade_status_check;
        END IF;
        ALTER TABLE public.trade_request_history
            ADD CONSTRAINT trade_request_history_trade_status_check
                CHECK ( (trade_status >= 0) AND (trade_status <= 5) );
        RAISE NOTICE 'trade_request_history_trade_status_check 제약조건을 0~5 범위로 재생성했습니다.';
    ELSE
        RAISE NOTICE 'trade_request_history 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다.';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'item'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'item' AND column_name = 'reserved_at'
        ) THEN
            ALTER TABLE public.item ADD COLUMN reserved_at TIMESTAMP(6);
            RAISE NOTICE 'item.reserved_at 컬럼을 추가했습니다.';
        END IF;

        -- 예약중 물품만 소수 > 부분 인덱스 (1분 주기 정리 쿼리용)
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_item_reserved_at'
        ) THEN
            CREATE INDEX idx_item_reserved_at ON public.item (reserved_at) WHERE reserved_at IS NOT NULL;
            RAISE NOTICE 'idx_item_reserved_at 인덱스를 생성했습니다.';
        END IF;
    ELSE
        RAISE NOTICE 'item 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '교환 요청 개선 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
