-- 외래키·조회 경로 인덱스 일괄 추가 (#844)
-- 대상 테이블이 작아 일반 CREATE INDEX 잠금 시간은 무시 가능 (Flyway 트랜잭션 안이라 CONCURRENTLY 불가)
-- 한 건 실패가 나머지를 되돌리지 않도록 건별 예외 처리
DO $$
DECLARE
    index_target RECORD;
    column_list_exists BOOLEAN;
BEGIN
    FOR index_target IN
        SELECT * FROM (VALUES
            -- 조회 이력: 하루 1회 기록 여부 확인(회원+물품+날짜), 물품별 조회, 일별 집계
            ('idx_view_history_member_item_viewed_date', 'view_history', ARRAY['member_member_id', 'item_item_id', 'viewed_date']),
            ('idx_view_history_item', 'view_history', ARRAY['item_item_id']),
            ('idx_view_history_viewed_date', 'view_history', ARRAY['viewed_date']),
            -- 물품 상세·목록의 이미지와 거래 방식
            ('idx_item_image_item', 'item_image', ARRAY['item_item_id']),
            ('idx_item_item_trade_options_item', 'item_item_trade_options', ARRAY['item_item_id']),
            -- 회원별 추천·선호 카테고리·위치
            ('idx_user_interaction_score_member', 'user_interaction_score', ARRAY['member_member_id']),
            ('idx_member_item_category_member', 'member_item_category', ARRAY['member_member_id']),
            ('idx_member_location_member', 'member_location', ARRAY['member_member_id']),
            -- 알림 목록: 회원별 최신순
            ('idx_notification_history_member_published_at', 'notification_history', ARRAY['member_member_id', 'published_at']),
            -- 푸시 대상 토큰
            ('idx_fcm_token_member', 'fcm_token', ARRAY['member_member_id']),
            -- 차단·숨김: 피드 제외 조건
            ('idx_member_block_blocked_member', 'member_block', ARRAY['blocked_member_id']),
            ('idx_hidden_item_member', 'hidden_item', ARRAY['member_member_id']),
            ('idx_hidden_item_item', 'hidden_item', ARRAY['item_item_id']),
            -- 거래 후기
            ('idx_trade_review_reviewed_member', 'trade_review', ARRAY['reviewed_member_member_id']),
            ('idx_trade_review_reviewer_member', 'trade_review', ARRAY['reviewer_member_member_id']),
            ('idx_trade_review_trade_request', 'trade_review', ARRAY['trade_request_history_trade_request_history_id']),
            ('idx_trade_review_tags_review', 'trade_review_trade_review_tags', ARRAY['trade_review_trade_review_id']),
            -- 거래 요청·신고의 컬렉션 테이블
            ('idx_trade_request_trade_options_request', 'trade_request_history_item_trade_options', ARRAY['trade_request_history_trade_request_history_id']),
            ('idx_item_report_reasons_report', 'item_report_item_report_reasons', ARRAY['item_report_item_report_id']),
            ('idx_member_report_reasons_report', 'member_report_member_report_reasons', ARRAY['member_report_member_report_id'])
        ) AS target(index_name, table_name, column_names)
    LOOP
        BEGIN
            -- 테이블·컬럼 전부 존재할 때만 생성 (환경별 스키마 차이 허용)
            SELECT count(*) = cardinality(index_target.column_names)
              INTO column_list_exists
              FROM information_schema.columns
             WHERE table_name = index_target.table_name
               AND column_name = ANY (index_target.column_names);

            IF NOT column_list_exists THEN
                RAISE NOTICE '인덱스 생략(테이블 또는 컬럼 없음): %', index_target.index_name;
            ELSIF NOT EXISTS (
                SELECT 1 FROM pg_indexes
                 WHERE tablename = index_target.table_name
                   AND indexname = index_target.index_name
            ) THEN
                EXECUTE format(
                    'CREATE INDEX %I ON %I (%s)',
                    index_target.index_name,
                    index_target.table_name,
                    (SELECT string_agg(quote_ident(column_name), ', ')
                       FROM unnest(index_target.column_names) AS column_name)
                );
                RAISE NOTICE '인덱스 생성: %', index_target.index_name;
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                RAISE WARNING '인덱스 생성 실패 %: %', index_target.index_name, SQLERRM;
        END;
    END LOOP;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '외래키 인덱스 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
