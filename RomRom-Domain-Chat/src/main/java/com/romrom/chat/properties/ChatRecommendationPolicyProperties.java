package com.romrom.chat.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai.chat.recommendation")
public class ChatRecommendationPolicyProperties {
  private int llmCooldownSeconds = 300;
  private int sameActionCooldownSeconds = 900;
  private int cacheTtlSeconds = 1800;
  private int tradeCompletionInactivitySeconds = 1800;
  private int tradeCompletionRetryCooldownSeconds = 600;
  private int maxPromptMessageLength = 120;
  private int trivialMessageLength = 2;
}
