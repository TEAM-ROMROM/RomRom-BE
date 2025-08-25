package com.romrom.ai.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vertex.ai.prompt")
public record AiPromptProperties(
    String name,
    String version,
    String description,
    String instruction,
    String responseSchemaJson,
    GenerationConfig generationConfig
) {

  public static record GenerationConfig(
      float temperature,
      int maxOutputTokens,
      String responseMimeType
  ) {

  }
}
