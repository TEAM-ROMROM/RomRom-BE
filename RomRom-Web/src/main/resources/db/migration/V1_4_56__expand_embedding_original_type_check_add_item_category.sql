-- embedding original_type 체크 제약조건 확장
-- V1.4.55: ITEM_CATEGORY(2) 포함하도록 제약조건 확장
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'embedding'
    ) THEN
        -- 기존 제약조건 삭제
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND constraint_name = 'embedding_original_type_check'
            AND table_name = 'embedding'
        ) THEN
            ALTER TABLE public.embedding
                DROP CONSTRAINT embedding_original_type_check;
            RAISE NOTICE '기존 embedding_original_type_check 제약조건을 삭제했습니다';
        END IF;

        -- 새로운 제약조건 추가 (0: ITEM, 1: CATEGORY, 2: ITEM_CATEGORY)
        ALTER TABLE public.embedding
            ADD CONSTRAINT embedding_original_type_check
                CHECK ( (original_type >= 0) AND (original_type <= 2) );
        RAISE NOTICE 'embedding_original_type_check 제약조건을 추가했습니다 (0~2 범위)';
    ELSE
        RAISE NOTICE 'embedding 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
