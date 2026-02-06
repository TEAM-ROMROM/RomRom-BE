--- 컬럼 추가
ALTER TABLE "member"
  ADD COLUMN IF NOT EXISTS is_activity_notification_agreed BOOLEAN;

ALTER TABLE "member"
  ADD COLUMN IF NOT EXISTS is_chat_notification_agreed BOOLEAN;

ALTER TABLE "member"
  ADD COLUMN IF NOT EXISTS is_content_notification_agreed BOOLEAN;

ALTER TABLE "member"
  ADD COLUMN IF NOT EXISTS is_trade_notification_agreed BOOLEAN;

-- 데이터 마이그레이션
-- 기존 is_notification_agreed 가 true 이면 신규 4개 컬럼 모두 true, 아니면 false
UPDATE "member"
SET is_activity_notification_agreed = COALESCE(is_notification_agreed, FALSE),
    is_chat_notification_agreed     = COALESCE(is_notification_agreed, FALSE),
    is_content_notification_agreed  = COALESCE(is_notification_agreed, FALSE),
    is_trade_notification_agreed    = COALESCE(is_notification_agreed, FALSE)
WHERE is_activity_notification_agreed IS NULL
   OR is_chat_notification_agreed IS NULL
   OR is_content_notification_agreed IS NULL
   OR is_trade_notification_agreed IS NULL;

-- 3) 기본값/NOT NULL 제약 반영 (엔티티와 정합)
ALTER TABLE "member"
  ALTER COLUMN is_activity_notification_agreed SET DEFAULT FALSE;
ALTER TABLE "member"
  ALTER COLUMN is_activity_notification_agreed SET NOT NULL;

ALTER TABLE "member"
  ALTER COLUMN is_chat_notification_agreed SET DEFAULT FALSE;
ALTER TABLE "member"
  ALTER COLUMN is_chat_notification_agreed SET NOT NULL;

ALTER TABLE "member"
  ALTER COLUMN is_content_notification_agreed SET DEFAULT FALSE;
ALTER TABLE "member"
  ALTER COLUMN is_content_notification_agreed SET NOT NULL;

ALTER TABLE "member"
  ALTER COLUMN is_trade_notification_agreed SET DEFAULT FALSE;
ALTER TABLE "member"
  ALTER COLUMN is_trade_notification_agreed SET NOT NULL;

-- 4) 기존 컬럼 제거 (엔티티에서 제거했으므로 DB도 정리)
ALTER TABLE "member"
  DROP COLUMN IF EXISTS is_notification_agreed;