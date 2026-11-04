-- 채팅 메시지 신고 테이블 (#860)
-- 컬럼 이름은 JPA 엔티티 ChatMessageReport 기본 명명과 동일 (ddl-auto update 와 충돌 없음)
-- 테이블·인덱스 건별 존재 확인 > 재실행해도 안전
DO $$
BEGIN
    -- 신고 본 테이블 (새 DB처럼 member 테이블이 아직 없으면 건너뜀 > JPA가 생성)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report'
    ) THEN
        CREATE TABLE chat_message_report (
            chat_message_report_id UUID PRIMARY KEY,
            created_date TIMESTAMP(6) NOT NULL,
            updated_date TIMESTAMP(6) NOT NULL,
            reporter_member_id UUID REFERENCES member (member_id),
            target_member_member_id UUID REFERENCES member (member_id),
            chat_room_id UUID NOT NULL,
            chat_message_id VARCHAR(64) NOT NULL,
            message_content_snapshot VARCHAR(2000),
            extra_comment VARCHAR(1000),
            status VARCHAR(255) NOT NULL DEFAULT 'PENDING'
        );
        RAISE NOTICE 'chat_message_report 테이블 생성';
    END IF;

    -- 신고 사유 컬렉션 테이블 (MemberReportReason 이름 저장)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report_reason'
    ) THEN
        CREATE TABLE chat_message_report_reason (
            chat_message_report_id UUID NOT NULL REFERENCES chat_message_report (chat_message_report_id),
            reason VARCHAR(255) NOT NULL
        );
        RAISE NOTICE 'chat_message_report_reason 테이블 생성';
    END IF;

    -- 중복 신고 확인 (메시지 + 신고자)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'chat_message_report' AND indexname = 'idx_chat_message_report_message_reporter'
    ) THEN
        CREATE INDEX idx_chat_message_report_message_reporter ON chat_message_report (chat_message_id, reporter_member_id);
    END IF;

    -- 관리자 목록: 접수 최신순
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'chat_message_report' AND indexname = 'idx_chat_message_report_created_date'
    ) THEN
        CREATE INDEX idx_chat_message_report_created_date ON chat_message_report (created_date DESC);
    END IF;

    -- 사유 컬렉션 조회
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'chat_message_report_reason'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename = 'chat_message_report_reason' AND indexname = 'idx_chat_message_report_reason_report'
    ) THEN
        CREATE INDEX idx_chat_message_report_reason_report ON chat_message_report_reason (chat_message_report_id);
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'V1_4_71 chat_message_report 마이그레이션 오류: %', SQLERRM;
END $$;
