-- system_config 테이블 생성 및 AI 설정 초기 데이터
-- V1.4.8: DB 기반 동적 설정 관리 시스템 도입

DO $$
BEGIN
    -- system_config 테이블 생성
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'system_config'
    ) THEN
        CREATE TABLE system_config (
            system_config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            config_key VARCHAR(100) NOT NULL,
            config_value TEXT,
            description VARCHAR(500),
            created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT uk_system_config_key UNIQUE (config_key)
        );
        RAISE NOTICE 'system_config 테이블이 생성되었습니다.';
    ELSE
        RAISE NOTICE 'system_config 테이블이 이미 존재합니다.';
    END IF;

    -- 인덱스 생성
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE tablename = 'system_config' AND indexname = 'idx_system_config_key'
    ) THEN
        CREATE INDEX idx_system_config_key ON system_config(config_key);
        RAISE NOTICE '인덱스 idx_system_config_key가 생성되었습니다.';
    END IF;

    -- AI 설정 초기 데이터 INSERT (이미 존재하면 무시)
    INSERT INTO system_config (config_key, config_value, description)
    VALUES
        ('ai.primary.provider', 'ollama', 'Primary AI 제공자 (ollama 또는 vertex)'),
        ('ai.fallback.provider', 'vertex', 'Fallback AI 제공자 (ollama 또는 vertex)'),
        ('ai.ollama.enabled', 'true', 'Ollama 활성화 여부'),
        ('ai.ollama.base-url', 'https://ai.suhsaechan.kr', 'Ollama(SuhAider) Base URL'),
        ('ai.ollama.chat-model', 'granite4:micro-h', 'Ollama 채팅 모델'),
        ('ai.ollama.embedding-model', 'embeddinggemma:latest', 'Ollama 임베딩 모델'),
        ('ai.vertex.enabled', 'true', 'Vertex AI 활성화 여부'),
        ('ai.vertex.generation-model', 'gemini-2.0-flash-lite-001', 'Vertex AI 생성 모델'),
        ('ai.vertex.embedding-model', 'text-embedding-005', 'Vertex AI 임베딩 모델'),
        ('ai.vertex.embedding-location', 'asia-northeast3', 'Vertex AI 임베딩 리전'),
        ('ai.vertex.generation-location', 'us-central1', 'Vertex AI 생성 리전')
    ON CONFLICT (config_key) DO NOTHING;

    RAISE NOTICE 'AI 설정 초기 데이터 INSERT 완료';

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'system_config 마이그레이션 중 오류 발생: %', SQLERRM;
END $$;
