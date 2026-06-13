DO $$
DECLARE
    tradeMemberColumnName TEXT;
    chatRoomColumnName TEXT;
BEGIN
    -- trade_request_history: 거래 양쪽 회원 기준 조회용 인덱스
    -- giveItem.member, takeItem.member는 Item 경유 조인이라 직접 컬럼이 아님.
    -- TradeRequestHistory 자체에 있는 take_item / give_item FK 인덱스를 보강.
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'trade_request_history'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trade_request_history' AND column_name = 'take_item_item_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_trade_request_history_take_item'
        ) THEN
            CREATE INDEX idx_trade_request_history_take_item
                ON trade_request_history (take_item_item_id);
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trade_request_history' AND column_name = 'give_item_item_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_trade_request_history_give_item'
        ) THEN
            CREATE INDEX idx_trade_request_history_give_item
                ON trade_request_history (give_item_item_id);
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'trade_request_history' AND column_name = 'trade_status'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_trade_request_history_trade_status_created_date'
        ) THEN
            CREATE INDEX idx_trade_request_history_trade_status_created_date
                ON trade_request_history (trade_status, created_date DESC);
        END IF;
    ELSE
        RAISE NOTICE 'trade_request_history 테이블이 존재하지 않습니다.';
    END IF;

    -- chat_room: 회원(trade_sender / trade_receiver) 기준 조회용 인덱스
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_room'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'chat_room' AND column_name = 'trade_sender_member_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_chat_room_trade_sender'
        ) THEN
            CREATE INDEX idx_chat_room_trade_sender
                ON chat_room (trade_sender_member_id);
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'chat_room' AND column_name = 'trade_receiver_member_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_chat_room_trade_receiver'
        ) THEN
            CREATE INDEX idx_chat_room_trade_receiver
                ON chat_room (trade_receiver_member_id);
        END IF;
    ELSE
        RAISE NOTICE 'chat_room 테이블이 존재하지 않습니다.';
    END IF;

    -- item_report: 신고자(member) 기준 조회용 인덱스
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item_report'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'item_report' AND column_name = 'member_member_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_item_report_member'
        ) THEN
            CREATE INDEX idx_item_report_member
                ON item_report (member_member_id, created_date DESC);
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'item_report' AND column_name = 'item_item_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_item_report_item'
        ) THEN
            CREATE INDEX idx_item_report_item
                ON item_report (item_item_id, created_date DESC);
        END IF;
    ELSE
        RAISE NOTICE 'item_report 테이블이 존재하지 않습니다.';
    END IF;

    -- member_report: 신고자(reporter), 피신고자(target_member) 기준 조회용 인덱스
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_report'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'member_report' AND column_name = 'reporter_member_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_member_report_reporter'
        ) THEN
            CREATE INDEX idx_member_report_reporter
                ON member_report (reporter_member_id, created_date DESC);
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'member_report' AND column_name = 'target_member_member_id'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_member_report_target_member'
        ) THEN
            CREATE INDEX idx_member_report_target_member
                ON member_report (target_member_member_id, created_date DESC);
        END IF;
    ELSE
        RAISE NOTICE 'member_report 테이블이 존재하지 않습니다.';
    END IF;

    -- item: 회원별 물품 조회 + 상태 + 삭제 여부 복합 인덱스
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'item' AND column_name = 'member_member_id'
        ) AND EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'item' AND column_name = 'is_deleted'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE indexname = 'idx_item_member_is_deleted_created_date'
        ) THEN
            CREATE INDEX idx_item_member_is_deleted_created_date
                ON item (member_member_id, is_deleted, created_date DESC);
        END IF;
    ELSE
        RAISE NOTICE 'item 테이블이 존재하지 않습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '오류 발생: %', SQLERRM;
END $$;
