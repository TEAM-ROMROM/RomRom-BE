-- Member 테이블에 total_like_count 컬럼 추가
ALTER TABLE member
  ADD COLUMN total_like_count integer NOT NULL DEFAULT 0;

-- 기존 Item.like_count 를 기준으로 Member.total_like_count 동기화
UPDATE member m
SET total_like_count = COALESCE(item_like_sum.total_like_count, 0)
FROM (
       SELECT i.member_member_id,
              COALESCE(SUM(i.like_count), 0) AS total_like_count
       FROM item i
       WHERE i.is_deleted = FALSE
       GROUP BY i.member_member_id

     ) AS item_like_sum
WHERE m.member_id = item_like_sum.member_member_id;