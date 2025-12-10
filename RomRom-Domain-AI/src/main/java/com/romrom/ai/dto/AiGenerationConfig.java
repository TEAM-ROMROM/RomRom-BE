package com.romrom.ai.dto;

import lombok.Builder;

/**
 * AI 생성 설정 DTO
 * 다양한 AI 제공자에서 공통으로 사용할 수 있는 설정
 */
@Builder
public record AiGenerationConfig(
    float temperature,
    int maxOutputTokens,
    String responseMimeType,
    String responseSchema
) {

  public static AiGenerationConfig defaultConfig() {
    return AiGenerationConfig.builder()
        .temperature(0.0f)
        .maxOutputTokens(1024)
        .responseMimeType("text/plain")
        .build();
  }

  public static AiGenerationConfig jsonConfig(String schema) {
    return AiGenerationConfig.builder()
        .temperature(0.0f)
        .maxOutputTokens(1024)
        .responseMimeType("application/json")
        .responseSchema(schema)
        .build();
  }
}
