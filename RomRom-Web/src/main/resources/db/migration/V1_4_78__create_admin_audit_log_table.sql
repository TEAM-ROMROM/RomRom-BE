-- 관리자 작업 기록 (#874)
-- admin_audit_log 테이블 + 인덱스 4개 (최근순, 관리자별, 작업 종류별, 대상별)
-- 컬럼 이름은 JPA 엔티티 명명과 동일 (ddl-auto update 와 충돌 없음), 블록마다 존재 확인 > 재실행해도 안전

DO $$
BEGIN
    -- member 테이블이 아직 없는 새 DB > 건너뜀 (JPA가 생성)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_audit_log'
    ) THEN
        CREATE TABLE admin_audit_log (
            admin_audit_log_id UUID PRIMARY KEY,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL,
            admin_member_id UUID,
            admin_email VARCHAR(255),
            action VARCHAR(60) NOT NULL,
            target_type VARCHAR(40),
            target_id VARCHAR(100),
            summary VARCHAR(200),
            request_summary TEXT,
            client_ip VARCHAR(64)
        );
        RAISE NOTICE 'admin_audit_log 테이블 생성';
    END IF;

    -- 목록 기본 정렬: 최근순
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_audit_log'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'admin_audit_log' AND indexname = 'idx_admin_audit_log_created_date'
    ) THEN
        CREATE INDEX idx_admin_audit_log_created_date ON admin_audit_log (created_date DESC);
    END IF;

    -- 관리자 필터
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_audit_log'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'admin_audit_log' AND indexname = 'idx_admin_audit_log_admin_created_date'
    ) THEN
        CREATE INDEX idx_admin_audit_log_admin_created_date ON admin_audit_log (admin_member_id, created_date);
    END IF;

    -- 작업 종류 필터
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_audit_log'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'admin_audit_log' AND indexname = 'idx_admin_audit_log_action_created_date'
    ) THEN
        CREATE INDEX idx_admin_audit_log_action_created_date ON admin_audit_log (action, created_date);
    END IF;

    -- 대상 조회 (특정 물품·회원에 대한 작업)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'admin_audit_log'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'admin_audit_log' AND indexname = 'idx_admin_audit_log_target'
    ) THEN
        CREATE INDEX idx_admin_audit_log_target ON admin_audit_log (target_type, target_id);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_78 admin_audit_log 마이그레이션 오류: %', SQLERRM;
END $$;
