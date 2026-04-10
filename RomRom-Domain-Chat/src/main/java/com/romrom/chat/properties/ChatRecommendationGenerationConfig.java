package com.romrom.chat.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatRecommendationGenerationConfig {
  private double temperature = 0.1d;
  private int maxOutputTokens = 120;
}
