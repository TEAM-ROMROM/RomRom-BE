-- 서버 계약 정리 (#858)
-- 1) member_location 동네 인증 시각·방식 (기존 회원 = null = 미인증 > 앱에서 재인증, 만료 없음)
-- 2) item 거래 희망 위치 행정구역 (서버 역지오코딩 없음 > 기존 물품은 null 유지, 앱이 좌표로 변환)
-- 3) system_config 동네 인증 거부 스위치 기본 꺼짐
DO $$
BEGIN
    -- member_location.location_verified_at
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_location'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member_location' AND column_name = 'location_verified_at'
    ) THEN
        ALTER TABLE member_location ADD COLUMN location_verified_at TIMESTAMP(6);
        RAISE NOTICE 'member_location.location_verified_at 컬럼이 추가되었습니다.';
    END IF;

    -- member_location.location_verification_method (GPS / MANUAL_OUTSIDE_SERVICE_AREA)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'member_location'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns WHERE table_name = 'member_location' AND column_name = 'location_verification_method'
    ) THEN
        ALTER TABLE member_location ADD COLUMN location_verification_method VARCHAR(255);
        RAISE NOTICE 'member_location.location_verification_method 컬럼이 추가되었습니다.';
    END IF;

    -- item 거래 희망 위치 행정구역 4개
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'item'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'trade_si_do'
        ) THEN
            ALTER TABLE item ADD COLUMN trade_si_do VARCHAR(255);
            RAISE NOTICE 'item.trade_si_do 컬럼이 추가되었습니다.';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'trade_si_gun_gu'
        ) THEN
            ALTER TABLE item ADD COLUMN trade_si_gun_gu VARCHAR(255);
            RAISE NOTICE 'item.trade_si_gun_gu 컬럼이 추가되었습니다.';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'trade_eup_myoen_dong'
        ) THEN
            ALTER TABLE item ADD COLUMN trade_eup_myoen_dong VARCHAR(255);
            RAISE NOTICE 'item.trade_eup_myoen_dong 컬럼이 추가되었습니다.';
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns WHERE table_name = 'item' AND column_name = 'trade_ri'
        ) THEN
            ALTER TABLE item ADD COLUMN trade_ri VARCHAR(255);
            RAISE NOTICE 'item.trade_ri 컬럼이 추가되었습니다.';
        END IF;
    ELSE
        RAISE NOTICE 'item 테이블이 존재하지 않아 행정구역 컬럼 추가를 건너뜁니다.';
    END IF;

    -- 동네 인증 거부 스위치 ("true" 일 때만 미인증 회원 물품 등록·교환 요청·채팅방 생성 403)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'system_config'
    ) THEN
        INSERT INTO system_config (config_key, config_value, description)
        VALUES ('location.verification.enforced', 'false', '동네 인증 거부 스위치 (true: 미인증 회원 물품 등록·교환 요청·채팅방 생성 거부)')
        ON CONFLICT (config_key) DO NOTHING;
        RAISE NOTICE 'location.verification.enforced 설정 INSERT 완료';
    ELSE
        RAISE NOTICE 'system_config 테이블이 존재하지 않아 동네 인증 스위치 INSERT를 건너뜁니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '서버 계약 정리(#858) 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
