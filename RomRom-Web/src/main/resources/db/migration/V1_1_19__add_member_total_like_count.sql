-- Member 테이블에 total_like_count 컬럼 추가
-- V1.1.19: 회원의 총 좋아요 수 집계 필드 추가
DO $$
BEGIN
    -- 1. member 테이블 존재 여부 확인
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'member'
    ) THEN
        -- 2. total_like_count 컬럼이 없으면 추가
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_schema = 'public'
            AND table_name = 'member' 
            AND column_name = 'total_like_count'
        ) THEN
            ALTER TABLE public.member
                ADD COLUMN total_like_count integer NOT NULL DEFAULT 0;
            RAISE NOTICE 'member 테이블에 total_like_count 컬럼을 추가했습니다';
        ELSE
            RAISE NOTICE 'total_like_count 컬럼이 이미 member 테이블에 존재합니다';
        END IF;
        
        -- 3. 기존 Item.like_count를 기준으로 Member.total_like_count 동기화
        -- item 테이블이 존재하는 경우에만 실행
        IF EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = 'public' 
            AND table_name = 'item'
        ) THEN
            UPDATE public.member m
            SET total_like_count = COALESCE(item_like_sum.total_like_count, 0)
            FROM (
                SELECT i.member_member_id,
                       COALESCE(SUM(i.like_count), 0) AS total_like_count
                FROM public.item i
                WHERE i.is_deleted = FALSE
                GROUP BY i.member_member_id
            ) AS item_like_sum
            WHERE m.member_id = item_like_sum.member_member_id;
            
            RAISE NOTICE '기존 Item.like_count를 기준으로 Member.total_like_count를 동기화했습니다';
        ELSE
            RAISE NOTICE 'item 테이블이 존재하지 않아 동기화를 건너뜁니다. JPA가 생성할 예정입니다';
        END IF;
    ELSE
        RAISE NOTICE 'member 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION 
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
        -- 오류가 발생해도 계속 진행 (Flyway가 실패로 처리하지 않음)
END $$;
