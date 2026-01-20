-- Member 테이블에 is_notification_agreed 컬럼 추가
-- V1.1.49: 회원 알림 수신 여부 구현

-- Member 테이블에 is_notification_agreed 컬럼 추가
-- V1.1.49: 회원 알림 수신 여부 구현
DO $$
BEGIN
    -- 1. member 테이블 존재 여부 확인
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'member'
    ) THEN
        -- 2. is_notification_agreed 컬럼이 없으면 추가
        IF NOT EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'member' AND column_name = 'is_notification_agreed'
        ) THEN
            -- boolean 타입으로 추가하며 기본값은 TRUE 설정
                ALTER TABLE public.member
                ADD COLUMN is_notification_agreed boolean NOT NULL DEFAULT TRUE;
                RAISE NOTICE 'member 테이블에 is_notification_agreed 컬럼을 추가했습니다';
        ELSE
                RAISE NOTICE 'is_notification_agreed 컬럼이 이미 member 테이블에 존재합니다';
        END IF;

    -- 3. 기존 회원의 상태를 업데이트
    UPDATE public.member SET is_notification_agreed = TRUE WHERE is_notification_agreed IS NULL;

    ELSE
        RAISE NOTICE 'member 테이블이 존재하지 않습니다. JPA가 생성할 예정입니다';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '마이그레이션 중 오류 발생: %', SQLERRM;
END $$;