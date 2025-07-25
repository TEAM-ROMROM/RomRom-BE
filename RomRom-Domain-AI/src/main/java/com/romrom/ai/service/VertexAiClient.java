package com.romrom.ai.service;

import com.google.genai.types.EmbedContentResponse;

public interface VertexAiClient {
  EmbedContentResponse generateEmbedding(String text);
}
