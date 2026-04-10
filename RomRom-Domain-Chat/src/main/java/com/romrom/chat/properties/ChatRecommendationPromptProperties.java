package com.romrom.chat.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ai.chat.recommendation.prompt")
public class ChatRecommendationPromptProperties {
  private boolean enabled = false;
  private String model;
  private String instruction;
  private int recentMessageLimit = 12;
  private ChatRecommendationGenerationConfig generationConfig = new ChatRecommendationGenerationConfig();
}
