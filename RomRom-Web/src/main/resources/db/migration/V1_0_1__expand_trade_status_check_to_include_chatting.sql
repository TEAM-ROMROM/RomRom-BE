ALTER TABLE romrom.public.trade_request_history
  DROP CONSTRAINT IF EXISTS trade_request_history_trade_status_check;

ALTER TABLE romrom.public.trade_request_history
  ADD CONSTRAINT trade_request_history_trade_status_check
    CHECK ( (trade_status >= 0) AND (trade_status <= 3) )