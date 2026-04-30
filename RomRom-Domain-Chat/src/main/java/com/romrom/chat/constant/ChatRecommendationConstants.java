package com.romrom.chat.constant;

import kr.suhsaechan.ai.model.JsonSchema;

public final class ChatRecommendationConstants {

  public static final String DEFAULT_CHAT_MODEL = "functiongemma";
  public static final JsonSchema RESPONSE_SCHEMA = JsonSchema.object("action", "string")
      .required("action");

  public static final String CACHE_KEY_PREFIX = "CHAT:RECOMMEND:CACHE:";
  public static final String LLM_COOLDOWN_KEY_PREFIX = "CHAT:RECOMMEND:LLM:";
  public static final String ACTION_COOLDOWN_KEY_PREFIX = "CHAT:RECOMMEND:ACTION:";

  public static final String CONFIG_KEY_LLM_COOLDOWN_SECONDS = "ai.chat.recommendation.llm-cooldown-seconds";
  public static final String CONFIG_KEY_SAME_ACTION_COOLDOWN_SECONDS = "ai.chat.recommendation.same-action-cooldown-seconds";
  public static final String CONFIG_KEY_CACHE_TTL_SECONDS = "ai.chat.recommendation.cache-ttl-seconds";
  public static final String CONFIG_KEY_TRADE_COMPLETION_INACTIVITY_SECONDS =
      "ai.chat.recommendation.trade-completion-inactivity-seconds";
  public static final String CONFIG_KEY_TRADE_COMPLETION_RETRY_COOLDOWN_SECONDS =
      "ai.chat.recommendation.trade-completion-retry-cooldown-seconds";

  private ChatRecommendationConstants() {
  }
}
