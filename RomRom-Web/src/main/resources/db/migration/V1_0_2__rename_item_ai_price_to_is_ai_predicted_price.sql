-- Item 테이블의 ai_price 컬럼을 is_ai_predicted_price로 이름 변경
-- V1.0.2: AI 가격 예측 필드명 변경
DO $$
BEGIN
    -- 1. item 테이블 존재 여부 확인
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'item'
    ) THEN
        -- 2. ai_price 컬럼이 존재하는지 확인
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public'
            AND table_name = 'item' 
            AND column_name = 'ai_price'
        ) THEN
            -- 3. is_ai_predicted_price 컬럼이 이미 존재하지 않는지 확인
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = 'public'
                AND table_name = 'item' 
                AND column_name = 'is_ai_predicted_price'
            ) THEN
                ALTER TABLE public.item
                    RENAME COLUMN ai_price TO is_ai_predicted_price;
                RAISE NOTICE 'item 테이블의 ai_price 컬럼을 is_ai_predicted_price로 이름 변경했습니다';
            ELSE
                RAISE NOTICE 'is_ai_predicted_price 컬럼이 이미 item 테이블에 존재합니다';
            END IF;
        ELSE
            -- ai_price 컬럼이 없으면 is_ai_predicted_price가 이미 있는지 확인
            IF EXISTS (
                SELECT 1 FROM information_schema.columns 
                WHERE table_schema = 'public'
                AND table_name = 'item' 
                AND column_name = 'is_ai_predicted_price'
            ) THEN
                RAISE NOTICE 'is_ai_predicted_price 컬럼이 이미 item 테이블에 존재합니다 (이름 변경 완료됨)';
            ELSE
                RAISE NOTICE 'ai_price 컬럼이 item 테이블에 존재하지 않습니다. JPA가 생성할 예정입니다';
            END IF;
        END IF;
    ELSE
        RAISE NOTICE 'item 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
        -- 오류가 발생해도 계속 진행 (Flyway가 실패로 처리하지 않음)
END $$;