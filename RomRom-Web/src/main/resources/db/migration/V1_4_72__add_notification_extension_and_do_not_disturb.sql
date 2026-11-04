-- 알림 확장 (#861)
-- 1) member 방해금지 시간 컬럼 3개 (기본 꺼짐, 22:00~08:00 KST)
-- 2) notification_history.notification_type 체크 제약 제거 (Hibernate 생성 enum 목록 > 새 타입 저장 실패 방지)
-- 3) announcement 선택 이미지·링크 컬럼
DO $$
BEGIN
    -- 1) 방해금지 사용 여부
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'is_do_not_disturb_enabled'
    ) THEN
        ALTER TABLE "member" ADD COLUMN is_do_not_disturb_enabled BOOLEAN NOT NULL DEFAULT FALSE;
        RAISE NOTICE 'member.is_do_not_disturb_enabled 컬럼이 추가되었습니다.';
    END IF;

    -- 1) 방해금지 시작 시각
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'do_not_disturb_start_time'
    ) THEN
        ALTER TABLE "member" ADD COLUMN do_not_disturb_start_time TIME(6) NOT NULL DEFAULT TIME '22:00';
        RAISE NOTICE 'member.do_not_disturb_start_time 컬럼이 추가되었습니다.';
    END IF;

    -- 1) 방해금지 종료 시각
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member' AND column_name = 'do_not_disturb_end_time'
    ) THEN
        ALTER TABLE "member" ADD COLUMN do_not_disturb_end_time TIME(6) NOT NULL DEFAULT TIME '08:00';
        RAISE NOTICE 'member.do_not_disturb_end_time 컬럼이 추가되었습니다.';
    END IF;

    -- 2) 알림 타입 체크 제약 제거 (값 검증은 애플리케이션 enum 담당)
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'notification_history'
          AND constraint_name = 'notification_history_notification_type_check'
    ) THEN
        ALTER TABLE notification_history DROP CONSTRAINT notification_history_notification_type_check;
        RAISE NOTICE 'notification_history_notification_type_check 제약조건을 삭제했습니다.';
    END IF;

    -- 3) 공지 이미지 URL
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'announcement'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'announcement' AND column_name = 'image_url'
    ) THEN
        ALTER TABLE announcement ADD COLUMN image_url VARCHAR(1000);
        RAISE NOTICE 'announcement.image_url 컬럼이 추가되었습니다.';
    END IF;

    -- 3) 공지 링크 URL
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'announcement'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'announcement' AND column_name = 'link_url'
    ) THEN
        ALTER TABLE announcement ADD COLUMN link_url VARCHAR(1000);
        RAISE NOTICE 'announcement.link_url 컬럼이 추가되었습니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '알림 확장 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
