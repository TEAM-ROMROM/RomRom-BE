-- 이미지 조건부 압축/업로드 병렬화 설정 초기값
-- V1.4.61: #733 FE 클라이언트 압축 대응 (BE 조건부 압축 + 업로드 병렬화)

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables WHERE table_name = 'system_config'
    ) THEN
        INSERT INTO system_config (config_key, config_value, description)
        VALUES
            ('image.compress.skip-content-type', 'image/webp', '압축 스킵 대상 contentType'),
            ('image.compress.skip-max-size-bytes', '512000', '이 용량(byte) 이하이고 스킵 contentType이면 압축 스킵'),
            ('image.upload.parallel-pool-size', '8', '이미지 업로드 병렬 스레드풀 크기 (서버 재시작 시 반영)')
        ON CONFLICT (config_key) DO NOTHING;

        RAISE NOTICE '이미지 압축/업로드 설정 초기 데이터 INSERT 완료';
    ELSE
        RAISE NOTICE 'system_config 테이블이 존재하지 않아 이미지 설정 INSERT를 건너뜁니다.';
    END IF;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING '이미지 설정 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
