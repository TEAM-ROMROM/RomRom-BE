-- trade_request_history 중복 거래요청 방지 및 목록 조회 성능 보강
-- TradeStatus는 JPA 기본 enum ordinal 저장 방식입니다.
--   PENDING=0, TRADED=1, CANCELED=2, CHATTING=3, TRADE_COMPLETE_REQUESTED=4
DO $$
DECLARE
    duplicate_active_pair_count BIGINT;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'trade_request_history'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'trade_request_history'
          AND column_name = 'take_item_item_id'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'trade_request_history'
          AND column_name = 'give_item_item_id'
    ) AND EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'trade_request_history'
          AND column_name = 'trade_status'
    ) THEN

        SELECT COUNT(*)
        INTO duplicate_active_pair_count
        FROM (
            SELECT
                LEAST(take_item_item_id, give_item_item_id) AS item_id_low,
                GREATEST(take_item_item_id, give_item_item_id) AS item_id_high
            FROM public.trade_request_history
            WHERE trade_status IN (0, 1, 3, 4)
            GROUP BY 1, 2
            HAVING COUNT(*) > 1
        ) duplicate_active_pairs;

        IF duplicate_active_pair_count > 0 THEN
            RAISE EXCEPTION
                '활성 거래요청 중복 물품 쌍이 %개 존재하여 uq_trh_active_item_pair 인덱스를 생성할 수 없습니다.',
                duplicate_active_pair_count;
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = 'uq_trh_active_item_pair'
        ) THEN
            CREATE UNIQUE INDEX uq_trh_active_item_pair
                ON public.trade_request_history (
                    LEAST(take_item_item_id, give_item_item_id),
                    GREATEST(take_item_item_id, give_item_item_id)
                )
                WHERE trade_status IN (0, 1, 3, 4);
            RAISE NOTICE 'uq_trh_active_item_pair 유니크 인덱스를 생성했습니다.';
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = 'idx_trh_take_active_created_date'
        ) THEN
            CREATE INDEX idx_trh_take_active_created_date
                ON public.trade_request_history (take_item_item_id, created_date DESC)
                WHERE trade_status IN (0, 1, 3, 4);
            RAISE NOTICE 'idx_trh_take_active_created_date 인덱스를 생성했습니다.';
        END IF;

        IF NOT EXISTS (
            SELECT 1
            FROM pg_indexes
            WHERE schemaname = 'public'
              AND indexname = 'idx_trh_give_active_created_date'
        ) THEN
            CREATE INDEX idx_trh_give_active_created_date
                ON public.trade_request_history (give_item_item_id, created_date DESC)
                WHERE trade_status IN (0, 1, 3, 4);
            RAISE NOTICE 'idx_trh_give_active_created_date 인덱스를 생성했습니다.';
        END IF;
    ELSE
        RAISE NOTICE 'trade_request_history 테이블 또는 필요한 컬럼이 없어 인덱스 생성을 건너뜁니다.';
    END IF;
END $$;
