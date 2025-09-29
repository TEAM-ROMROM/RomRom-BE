DO
$$
  BEGIN
    --- ai_price -> is_ai_predicted_price
    ALTER TABLE romrom.public.item
      RENAME COLUMN ai_price TO is_ai_predicted_price;
  END;
$$