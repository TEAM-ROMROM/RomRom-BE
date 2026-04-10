package com.romrom.chat.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRecommendationGenerationConfig {
  private double temperature = 0.0d;
  private int maxOutputTokens = 24;
}
