package com.romrom.ai.service;

import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

/**
 * Vertex AI 전용 인터페이스 (Google Gen AI SDK 타입 반환)
 * 기존 호환성 유지를 위해 보존
 * deprecated
 */
public interface VertexAiClient {
  EmbedContentResponse generateEmbeddingResponse(String text);
  int getItemPricePrediction(String inputText);
  GenerateContentResponse generateContentResponse(String text);
  GenerateContentResponse generateContentResponse(String text, GenerateContentConfig config);
}
