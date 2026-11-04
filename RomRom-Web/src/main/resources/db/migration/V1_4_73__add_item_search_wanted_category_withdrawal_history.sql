-- 물품 검색·희망 교환 카테고리·탈퇴 기록·닉네임 규칙 스위치 (#862)
-- 블록별 예외 처리 (한 블록 실패가 나머지를 되돌리지 않음)

-- 1. 희망 교환 카테고리 컬렉션 테이블 (Item.wantedItemCategories, JPA 매핑과 같은 이름)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'item')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'item_wanted_item_category') THEN
        CREATE TABLE item_wanted_item_category (
            item_item_id UUID NOT NULL REFERENCES item (item_id),
            wanted_item_category VARCHAR(50)
        );
        RAISE NOTICE 'item_wanted_item_category 테이블 생성';
    ELSE
        RAISE NOTICE 'item_wanted_item_category 생략 (item 없음 또는 이미 존재)';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'item_wanted_item_category')
       AND NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_item_wanted_item_category_item') THEN
        CREATE INDEX idx_item_wanted_item_category_item ON item_wanted_item_category (item_item_id);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '희망 교환 카테고리 테이블 마이그레이션 오류: %', SQLERRM;
END $$;

-- 2. 회원 탈퇴 기록 (MemberWithdrawalHistory, 회원 FK 없음)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'member_withdrawal_history') THEN
        CREATE TABLE member_withdrawal_history (
            member_withdrawal_history_id UUID PRIMARY KEY,
            member_id UUID NOT NULL,
            withdrawal_reason VARCHAR(50),
            withdrawal_reason_detail VARCHAR(500),
            canceled_trade_request_count INTEGER NOT NULL,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL
        );
        CREATE INDEX idx_member_withdrawal_history_created_date ON member_withdrawal_history (created_date);
        RAISE NOTICE 'member_withdrawal_history 테이블 생성';
    ELSE
        RAISE NOTICE 'member_withdrawal_history 이미 존재 > 생략';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '탈퇴 기록 테이블 마이그레이션 오류: %', SQLERRM;
END $$;

-- 3. 물품 검색 trigram 인덱스: pg_trgm 설치 가능할 때만 (권한 없음·미제공 > 인덱스 없이 일반 LIKE 검색)
-- 검색 쿼리 lower(컬럼) LIKE '%검색어%' 와 같은 식으로 생성 (3자 이상 검색어에 사용)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm')
       AND EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'pg_trgm') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS pg_trgm;
            RAISE NOTICE 'pg_trgm 확장 설치';
        EXCEPTION
            WHEN OTHERS THEN
                RAISE NOTICE 'pg_trgm 설치 불가 > trigram 인덱스 생략: %', SQLERRM;
        END;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm')
       AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'item') THEN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_item_item_name_trgm') THEN
            CREATE INDEX idx_item_item_name_trgm ON item USING gin (lower(item_name) gin_trgm_ops);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_item_item_description_trgm') THEN
            CREATE INDEX idx_item_item_description_trgm ON item USING gin (lower(item_description) gin_trgm_ops);
        END IF;
        RAISE NOTICE '물품 검색 trigram 인덱스 확인 완료';
    ELSE
        RAISE NOTICE 'pg_trgm 없음 > 물품 검색 trigram 인덱스 생략';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '물품 검색 인덱스 마이그레이션 오류: %', SQLERRM;
END $$;

-- 4. 닉네임 규칙 서버 검증 스위치 (기본 false: 구버전 앱 보호, 새 앱 보급 후 true)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_config') THEN
        INSERT INTO system_config (config_key, config_value, description)
        VALUES ('member.nickname.rule-enforced', 'false', '닉네임 규칙 서버 검증 (2~20자, 한글·영문·숫자·밑줄, 예약어) 사용 여부')
        ON CONFLICT (config_key) DO NOTHING;
    ELSE
        RAISE NOTICE 'system_config 없음 > 닉네임 규칙 스위치 생략';
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '닉네임 규칙 스위치 마이그레이션 오류: %', SQLERRM;
END $$;
