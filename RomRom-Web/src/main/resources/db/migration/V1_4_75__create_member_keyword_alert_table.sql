-- 회원 키워드 알림 테이블 (#873)
-- 컬럼 이름은 JPA 엔티티 MemberKeywordAlert 명명과 동일 (ddl-auto update 와 충돌 없음)
-- 테이블·제약·인덱스 건별 존재 확인 > 재실행해도 안전
DO $$
BEGIN
    -- 키워드 본 테이블 (새 DB처럼 member 테이블이 아직 없으면 건너뜀 > JPA가 생성)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_keyword_alert'
    ) THEN
        CREATE TABLE member_keyword_alert (
            keyword_alert_id UUID PRIMARY KEY,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL,
            member_id UUID NOT NULL REFERENCES member (member_id),
            keyword VARCHAR(50) NOT NULL,
            normalized_keyword VARCHAR(50) NOT NULL
        );
        RAISE NOTICE 'member_keyword_alert 테이블 생성';
    END IF;

    -- 회원별 같은 정규화 키워드 중복 금지
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_keyword_alert'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_member_keyword_alert_member_normalized_keyword'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'member_keyword_alert' AND indexname = 'uk_member_keyword_alert_member_normalized_keyword'
    ) THEN
        ALTER TABLE member_keyword_alert
            ADD CONSTRAINT uk_member_keyword_alert_member_normalized_keyword UNIQUE (member_id, normalized_keyword);
    END IF;

    -- 새 물품 매칭: 정규화 키워드 조회
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_keyword_alert'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'member_keyword_alert' AND indexname = 'idx_member_keyword_alert_normalized_keyword'
    ) THEN
        CREATE INDEX idx_member_keyword_alert_normalized_keyword ON member_keyword_alert (normalized_keyword);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_75 member_keyword_alert 마이그레이션 오류: %', SQLERRM;
END $$;
