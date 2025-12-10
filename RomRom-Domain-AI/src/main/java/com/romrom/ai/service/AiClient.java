package com.romrom.ai.service;

import com.romrom.ai.dto.AiGenerationConfig;

/**
 * AI 클라이언트 공통 인터페이스
 * Ollama, Vertex AI 등 다양한 AI 제공자를 추상화
 */
public interface AiClient {

  /**
   * 텍스트 임베딩 생성
   *
   * @param text 임베딩할 텍스트
   * @return 임베딩 벡터 (float 배열)
   */
  float[] generateEmbedding(String text);

  /**
   * 텍스트 생성 (기본 설정)
   *
   * @param prompt 프롬프트 텍스트
   * @return 생성된 텍스트
   */
  String generateContent(String prompt);

  /**
   * 텍스트 생성 (설정 포함)
   *
   * @param prompt 프롬프트 텍스트
   * @param config 생성 설정
   * @return 생성된 텍스트
   */
  String generateContent(String prompt, AiGenerationConfig config);

  /**
   * 아이템 가격 예측
   *
   * @param inputText 아이템 정보 텍스트
   * @return 예측 가격 (KRW)
   */
  int getItemPricePrediction(String inputText);

  /**
   * AI 클라이언트 이름 반환 (로깅용)
   *
   * @return 클라이언트 이름 (예: "Ollama", "VertexAI")
   */
  String getClientName();
}
