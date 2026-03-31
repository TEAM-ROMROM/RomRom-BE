-- embedding (original_id, original_type) 유니크 제약조건 추가
-- V1.4.56: 동시 초기화 시 중복 삽입 방지
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'embedding'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_schema = 'public'
            AND constraint_name = 'uq_embedding_original_id_original_type'
            AND table_name = 'embedding'
        ) THEN
            ALTER TABLE public.embedding
                ADD CONSTRAINT uq_embedding_original_id_original_type
                    UNIQUE (original_id, original_type);
            RAISE NOTICE 'uq_embedding_original_id_original_type 유니크 제약조건을 추가했습니다';
        ELSE
            RAISE NOTICE 'uq_embedding_original_id_original_type 이미 존재합니다. 스킵합니다';
        END IF;
    ELSE
        RAISE NOTICE 'embedding 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
