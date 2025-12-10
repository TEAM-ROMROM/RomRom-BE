package com.romrom.ai.service;

import com.romrom.ai.dto.AiGenerationConfig;
import com.romrom.ai.properties.OllamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 기본 AI 클라이언트
 * Primary: Ollama (ollama.enabled=true 일 때)
 * Fallback: Vertex AI (Ollama 실패 시 또는 ollama.enabled=false 일 때)
 */
@Service
@Primary
@Slf4j
public class DefaultAiClient implements AiClient {

  private final AiClient ollamaClient;
  private final AiClient vertexClient;
  private final OllamaProperties ollamaProperties;

  public DefaultAiClient(
      @Qualifier("ollamaAiClient") AiClient ollamaClient,
      @Qualifier("vertexAiClient") AiClient vertexClient,
      OllamaProperties ollamaProperties
  ) {
    this.ollamaClient = ollamaClient;
    this.vertexClient = vertexClient;
    this.ollamaProperties = ollamaProperties;
  }

  @Override
  public String getClientName() {
    return "Default(Ollama→VertexAI)";
  }

  @Override
  public float[] generateEmbedding(String text) {
    if (!ollamaProperties.isEnabled()) {
      log.debug("[DefaultAI] Ollama 비활성화됨, VertexAI 사용");
      return vertexClient.generateEmbedding(text);
    }

    try {
      log.debug("[DefaultAI] Ollama로 임베딩 생성 시도");
      return ollamaClient.generateEmbedding(text);
    } catch (Exception e) {
      log.warn("[DefaultAI] Ollama 임베딩 실패, VertexAI로 전환: {}", e.getMessage());
      return vertexClient.generateEmbedding(text);
    }
  }

  @Override
  public String generateContent(String prompt) {
    if (!ollamaProperties.isEnabled()) {
      log.debug("[DefaultAI] Ollama 비활성화됨, VertexAI 사용");
      return vertexClient.generateContent(prompt);
    }

    try {
      log.debug("[DefaultAI] Ollama로 텍스트 생성 시도");
      return ollamaClient.generateContent(prompt);
    } catch (Exception e) {
      log.warn("[DefaultAI] Ollama 텍스트 생성 실패, VertexAI로 전환: {}", e.getMessage());
      return vertexClient.generateContent(prompt);
    }
  }

  @Override
  public String generateContent(String prompt, AiGenerationConfig config) {
    if (!ollamaProperties.isEnabled()) {
      log.debug("[DefaultAI] Ollama 비활성화됨, VertexAI 사용");
      return vertexClient.generateContent(prompt, config);
    }

    try {
      log.debug("[DefaultAI] Ollama로 텍스트 생성 시도 (설정 포함)");
      return ollamaClient.generateContent(prompt, config);
    } catch (Exception e) {
      log.warn("[DefaultAI] Ollama 텍스트 생성 실패, VertexAI로 전환: {}", e.getMessage());
      return vertexClient.generateContent(prompt, config);
    }
  }

  @Override
  public int getItemPricePrediction(String inputText) {
    if (!ollamaProperties.isEnabled()) {
      log.debug("[DefaultAI] Ollama 비활성화됨, VertexAI 사용");
      return vertexClient.getItemPricePrediction(inputText);
    }

    try {
      log.debug("[DefaultAI] Ollama로 가격 예측 시도");
      return ollamaClient.getItemPricePrediction(inputText);
    } catch (Exception e) {
      log.warn("[DefaultAI] Ollama 가격 예측 실패, VertexAI로 전환: {}", e.getMessage());
      return vertexClient.getItemPricePrediction(inputText);
    }
  }
}
