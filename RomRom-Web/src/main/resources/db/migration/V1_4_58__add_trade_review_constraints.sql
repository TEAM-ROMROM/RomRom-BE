-- trade_review 테이블 무결성 제약 추가
--   tradeRequestHistory → trade_request_history_trade_request_history_id
--   reviewerMember      → reviewer_member_member_id
--   reviewedMember      → reviewed_member_member_id
-- 1. FK 컬럼 NOT NULL 제약
-- 2. (trade_request_history_trade_request_history_id, reviewer_member_member_id) 복합 유니크 제약 → 동시성 환경에서 중복 후기 방지
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'trade_review'
    ) THEN

        -- trade_request_history_trade_request_history_id NOT NULL
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'trade_review'
            AND column_name = 'trade_request_history_trade_request_history_id'
            AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE public.trade_review
                ALTER COLUMN trade_request_history_trade_request_history_id SET NOT NULL;
            RAISE NOTICE 'trade_request_history_trade_request_history_id NOT NULL 제약을 추가했습니다';
        END IF;

        -- reviewer_member_member_id NOT NULL
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'trade_review'
            AND column_name = 'reviewer_member_member_id'
            AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE public.trade_review
                ALTER COLUMN reviewer_member_member_id SET NOT NULL;
            RAISE NOTICE 'reviewer_member_member_id NOT NULL 제약을 추가했습니다';
        END IF;

        -- reviewed_member_member_id NOT NULL
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public'
            AND table_name = 'trade_review'
            AND column_name = 'reviewed_member_member_id'
            AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE public.trade_review
                ALTER COLUMN reviewed_member_member_id SET NOT NULL;
            RAISE NOTICE 'reviewed_member_member_id NOT NULL 제약을 추가했습니다';
        END IF;

        -- (trade_request_history_trade_request_history_id, reviewer_member_member_id) 복합 유니크 제약
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND constraint_name = 'uk_trade_review_history_reviewer'
            AND table_name = 'trade_review'
        ) THEN
            ALTER TABLE public.trade_review
                ADD CONSTRAINT uk_trade_review_history_reviewer
                    UNIQUE (trade_request_history_trade_request_history_id, reviewer_member_member_id);
            RAISE NOTICE 'uk_trade_review_history_reviewer 유니크 제약을 추가했습니다';
        ELSE
            RAISE NOTICE 'uk_trade_review_history_reviewer 이미 존재합니다. 스킵합니다';
        END IF;

    ELSE
        RAISE NOTICE 'trade_review 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
