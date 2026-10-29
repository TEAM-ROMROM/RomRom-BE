package com.romrom.chat.constant;

import kr.suhsaechan.ai.model.JsonSchema;

public final class ChatRecommendationConstants {

  public static final String DEFAULT_CHAT_MODEL = "functiongemma";
  public static final JsonSchema RESPONSE_SCHEMA = JsonSchema.object("action", "string")
      .required("action");

  public static final String CACHE_KEY_PREFIX = "CHAT:RECOMMEND:CACHE:";
  public static final String LLM_COOLDOWN_KEY_PREFIX = "CHAT:RECOMMEND:LLM:";
  public static final String ACTION_COOLDOWN_KEY_PREFIX = "CHAT:RECOMMEND:ACTION:";

  private ChatRecommendationConstants() {
  }
}
