-- 푸시 고도화 (#874)
-- 1) fcm_token: 중복 토큰 정리(최근 갱신 1행 유지, 빈 토큰 삭제) > 토큰 unique > 갱신일 인덱스(90일 정리)
-- 2) push_campaign·push_campaign_open 테이블 (컬럼 이름은 JPA 엔티티 명명과 동일, ddl-auto update 와 충돌 없음)
-- 3) notification_history.body 2000자 (캠페인 본문 1000자 + 광고 수신거부 안내)
-- 블록마다 존재 확인 > 재실행해도 안전, 블록 실패는 WARNING 후 다음 블록 진행

-- 1) fcm_token 중복 정리 + unique (같은 블록: unique 실패 시 정리도 함께 롤백)
DO $$
DECLARE
    removed_empty_token_count INTEGER := 0;
    removed_duplicate_token_count INTEGER := 0;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'fcm_token'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_fcm_token_token'
    ) THEN
        DELETE FROM fcm_token WHERE token IS NULL OR token = '';
        GET DIAGNOSTICS removed_empty_token_count = ROW_COUNT;

        WITH ranked_fcm_token AS (
            SELECT fcm_token_id,
                   ROW_NUMBER() OVER (
                       PARTITION BY token
                       ORDER BY updated_date DESC, created_date DESC, fcm_token_id
                   ) AS token_rank
            FROM fcm_token
        )
        DELETE FROM fcm_token
        WHERE fcm_token_id IN (SELECT fcm_token_id FROM ranked_fcm_token WHERE token_rank > 1);
        GET DIAGNOSTICS removed_duplicate_token_count = ROW_COUNT;

        RAISE NOTICE 'fcm_token 정리: 빈 토큰 % 행, 중복 토큰 % 행 삭제', removed_empty_token_count, removed_duplicate_token_count;

        ALTER TABLE fcm_token ADD CONSTRAINT uk_fcm_token_token UNIQUE (token);
        RAISE NOTICE 'uk_fcm_token_token 제약 추가';
    END IF;

    -- 오래된 토큰 정리 조회
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'fcm_token'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'fcm_token' AND indexname = 'idx_fcm_token_updated_date'
    ) THEN
        CREATE INDEX idx_fcm_token_updated_date ON fcm_token (updated_date);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_76 fcm_token 마이그레이션 오류: %', SQLERRM;
END $$;

-- 2) push_campaign·push_campaign_open (member 테이블이 아직 없는 새 DB > 건너뜀, JPA가 생성)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'push_campaign'
    ) THEN
        CREATE TABLE push_campaign (
            push_campaign_id UUID PRIMARY KEY,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL,
            title VARCHAR(100) NOT NULL,
            body VARCHAR(1000) NOT NULL,
            image_url VARCHAR(1000),
            deep_link VARCHAR(1000),
            purpose VARCHAR(30) NOT NULL,
            target_type VARCHAR(30) NOT NULL,
            target_filter TEXT,
            status VARCHAR(30) NOT NULL,
            scheduled_at TIMESTAMP(6) NOT NULL,
            started_at TIMESTAMP(6),
            completed_at TIMESTAMP(6),
            target_member_count INTEGER NOT NULL DEFAULT 0,
            sent_count INTEGER NOT NULL DEFAULT 0,
            failure_count INTEGER NOT NULL DEFAULT 0,
            removed_token_count INTEGER NOT NULL DEFAULT 0,
            opened_count INTEGER NOT NULL DEFAULT 0,
            created_by_admin_id UUID,
            failure_reason VARCHAR(500)
        );
        RAISE NOTICE 'push_campaign 테이블 생성';
    END IF;

    -- 예약 스케줄러: 상태 + 예약 시각
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'push_campaign'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'push_campaign' AND indexname = 'idx_push_campaign_status_scheduled_at'
    ) THEN
        CREATE INDEX idx_push_campaign_status_scheduled_at ON push_campaign (status, scheduled_at);
    END IF;

    -- 관리자 목록: 최근 생성순
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'push_campaign'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'push_campaign' AND indexname = 'idx_push_campaign_created_date'
    ) THEN
        CREATE INDEX idx_push_campaign_created_date ON push_campaign (created_date);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'push_campaign_open'
    ) THEN
        CREATE TABLE push_campaign_open (
            push_campaign_open_id UUID PRIMARY KEY,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL,
            push_campaign_id UUID NOT NULL,
            member_id UUID NOT NULL
        );
        RAISE NOTICE 'push_campaign_open 테이블 생성';
    END IF;

    -- 회원·캠페인당 열람 1회 (INSERT ... ON CONFLICT 대상)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'push_campaign_open'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_push_campaign_open_campaign_member'
    ) THEN
        ALTER TABLE push_campaign_open
            ADD CONSTRAINT uk_push_campaign_open_campaign_member UNIQUE (push_campaign_id, member_id);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_76 push_campaign 마이그레이션 오류: %', SQLERRM;
END $$;

-- 3) 알림함 본문 길이 확장 (짧을 때만)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'notification_history' AND column_name = 'body'
          AND character_maximum_length IS NOT NULL AND character_maximum_length < 2000
    ) THEN
        ALTER TABLE notification_history ALTER COLUMN body TYPE VARCHAR(2000);
        RAISE NOTICE 'notification_history.body VARCHAR(2000) 으로 확장';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_76 notification_history 마이그레이션 오류: %', SQLERRM;
END $$;
