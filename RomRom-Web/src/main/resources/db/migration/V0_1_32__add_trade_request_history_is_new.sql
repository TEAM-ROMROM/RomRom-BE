-- is_new 컬럼 추가
ALTER TABLE romrom.public.trade_request_history
  ADD COLUMN IF NOT EXISTS is_new boolean DEFAULT TRUE;

-- 기존 데이터 백필
UPDATE romrom.public.trade_request_history
SET is_new = TRUE
WHERE is_new IS NULL;

-- NOT_NULL 제약조건 추가
ALTER TABLE romrom.public.trade_request_history
  ALTER COLUMN is_new SET NOT NULL;