package com.romrom.common.constant;

/**
 * system_config 테이블(Redis 캐시 system:config) 설정 키 단일 원본
 * 키 문자열 변경 시 Flyway 마이그레이션의 INSERT 키와 함께 변경
 */
public final class SystemConfigKeys {

  // 접두사 (getByPrefix 조회용)
  public static final String AI_PREFIX = "ai.";
  public static final String APP_PREFIX = "app.";

  // 서버 점검 모드
  // "true" / "false"
  public static final String SERVER_MAINTENANCE_ENABLED = "server.maintenance.enabled";
  public static final String SERVER_MAINTENANCE_MESSAGE = "server.maintenance.message";
  public static final String SERVER_MAINTENANCE_END_TIME = "server.maintenance.end-time";

  // 앱 버전·스토어
  // semver 형식 (예: 1.6.107)
  public static final String APP_MIN_VERSION = "app.min.version";
  public static final String APP_LATEST_VERSION = "app.latest.version";
  public static final String APP_STORE_ANDROID = "app.store.android";
  public static final String APP_STORE_IOS = "app.store.ios";

  // AI 프로바이더
  public static final String AI_PRIMARY_PROVIDER = "ai.primary.provider";
  public static final String AI_FALLBACK_PROVIDER = "ai.fallback.provider";
  public static final String AI_OLLAMA_ENABLED = "ai.ollama.enabled";
  public static final String AI_OLLAMA_BASE_URL = "ai.ollama.base-url";
  public static final String AI_OLLAMA_CHAT_MODEL = "ai.ollama.chat-model";
  public static final String AI_OLLAMA_EMBEDDING_MODEL = "ai.ollama.embedding-model";
  public static final String AI_VERTEX_ENABLED = "ai.vertex.enabled";
  public static final String AI_VERTEX_GENERATION_MODEL = "ai.vertex.generation-model";
  public static final String AI_VERTEX_EMBEDDING_MODEL = "ai.vertex.embedding-model";
  public static final String AI_VERTEX_GENERATION_LOCATION = "ai.vertex.generation-location";
  public static final String AI_VERTEX_EMBEDDING_LOCATION = "ai.vertex.embedding-location";

  // AI 프롬프트
  // {{INPUT_TEXT}} 치환 템플릿
  public static final String AI_PROMPT_PRICE_PREDICTION_INSTRUCTION = "ai.prompt.price-prediction.instruction";

  // 채팅 행동 추천 (비어 있으면 yml 기본값 사용)
  public static final String CHAT_RECOMMENDATION_PROMPT_INSTRUCTION = "ai.chat.recommendation.prompt.instruction";
  // "true" / "false"
  public static final String CHAT_RECOMMENDATION_PROMPT_ENABLED = "ai.chat.recommendation.prompt.enabled";
  public static final String CHAT_RECOMMENDATION_MODEL = "ai.chat.recommendation.model";
  public static final String CHAT_RECOMMENDATION_LLM_COOLDOWN_SECONDS = "ai.chat.recommendation.llm-cooldown-seconds";
  public static final String CHAT_RECOMMENDATION_SAME_ACTION_COOLDOWN_SECONDS =
      "ai.chat.recommendation.same-action-cooldown-seconds";
  public static final String CHAT_RECOMMENDATION_CACHE_TTL_SECONDS = "ai.chat.recommendation.cache-ttl-seconds";
  public static final String CHAT_RECOMMENDATION_TRADE_COMPLETION_INACTIVITY_SECONDS =
      "ai.chat.recommendation.trade-completion-inactivity-seconds";
  public static final String CHAT_RECOMMENDATION_TRADE_COMPLETION_RETRY_COOLDOWN_SECONDS =
      "ai.chat.recommendation.trade-completion-retry-cooldown-seconds";

  // 이미지 압축·업로드
  public static final String IMAGE_COMPRESS_SKIP_CONTENT_TYPE = "image.compress.skip-content-type";
  // 압축 스킵 최대 용량 (byte, 0 이상)
  public static final String IMAGE_COMPRESS_SKIP_MAX_SIZE_BYTES = "image.compress.skip-max-size-bytes";
  // 업로드 스레드풀 크기 (서버 재시작 시 반영)
  public static final String IMAGE_UPLOAD_PARALLEL_POOL_SIZE = "image.upload.parallel-pool-size";

  // 관리자 알림·SMTP
  public static final String ADMIN_ALERT_EMAIL = "admin.alert.email";
  // 같은 신고 대상 알림 재발송 간격 (분)
  public static final String ADMIN_ALERT_THROTTLE_MINUTES = "admin.alert.throttle.minutes";
  public static final String MAIL_SMTP_HOST = "mail.smtp.host";
  public static final String MAIL_SMTP_PORT = "mail.smtp.port";
  public static final String MAIL_SMTP_USERNAME = "mail.smtp.username";
  public static final String MAIL_SMTP_PASSWORD = "mail.smtp.password";

  // UGC 필터
  // 정규식 패턴 JSON 배열 (예: ["욕설1", "욕설2"])
  public static final String UGC_FILTER_PATTERNS = "ugc.filter.patterns";

  private SystemConfigKeys() {
  }
}
