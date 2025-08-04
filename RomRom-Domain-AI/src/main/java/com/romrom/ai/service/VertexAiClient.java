package com.romrom.ai.service;

import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

public interface VertexAiClient {
  EmbedContentResponse generateEmbedding(String text);
  int getItemPricePrediction(String inputText);
  GenerateContentResponse generateContent(String text);
  GenerateContentResponse generateContent(String text, GenerateContentConfig config);
}
