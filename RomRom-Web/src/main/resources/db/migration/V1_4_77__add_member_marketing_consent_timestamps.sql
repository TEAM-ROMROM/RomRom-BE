-- 마케팅 수신 동의 기록 (#874)
-- 1) member.marketing_consent_changed_at (동의 변경 시각), marketing_consent_reminded_at (2년 안내 시각)
-- 2) 기존 동의자 변경 시각 = updated_date (기록 이전 동의 시각 추정값)
-- 3) 2년 안내 대상 조회 인덱스 (동의 중 회원만)
DO $$
DECLARE
    backfilled_member_count INTEGER := 0;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'marketing_consent_changed_at'
    ) THEN
        ALTER TABLE "member" ADD COLUMN marketing_consent_changed_at TIMESTAMP(6);
        RAISE NOTICE 'member.marketing_consent_changed_at 컬럼 추가';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'marketing_consent_reminded_at'
    ) THEN
        ALTER TABLE "member" ADD COLUMN marketing_consent_reminded_at TIMESTAMP(6);
        RAISE NOTICE 'member.marketing_consent_reminded_at 컬럼 추가';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'marketing_consent_changed_at'
    ) THEN
        UPDATE "member" SET marketing_consent_changed_at = updated_date
        WHERE is_marketing_info_agreed = true AND marketing_consent_changed_at IS NULL;
        GET DIAGNOSTICS backfilled_member_count = ROW_COUNT;
        RAISE NOTICE '기존 마케팅 동의자 변경 시각 채움: % 행', backfilled_member_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'marketing_consent_reminded_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'member' AND indexname = 'idx_member_marketing_consent_reminder'
    ) THEN
        CREATE INDEX idx_member_marketing_consent_reminder
            ON "member" (GREATEST(marketing_consent_changed_at, marketing_consent_reminded_at))
            WHERE is_marketing_info_agreed = true AND is_deleted = false;
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_77 member 마케팅 동의 마이그레이션 오류: %', SQLERRM;
END $$;
