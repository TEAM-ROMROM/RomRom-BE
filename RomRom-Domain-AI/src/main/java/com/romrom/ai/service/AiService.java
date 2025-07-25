package com.romrom.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

  private final VertexAiClient vertexAiClient;

  // AI 관련 기능들이 여기에 구현될 예정
  public String processAiRequest(String input) {
    return "AI processing: " + input;
  }

  /**
   * 텍스트를 기반으로 임베딩 벡터 생성
   *
   * @param text 임베딩을 생성할 텍스트
   * @return 384차원 임베딩 벡터 (Vertex AI text-embedding-005 모델 사용)
   */
  public float[] generateEmbedding(String text) {
    try {
      log.debug("임베딩 생성 요청: {}", text);

      // Vertex AI를 사용한 실제 임베딩 생성
      float[] embedding = vertexAiClient.getTextEmbedding(text);

      log.debug("임베딩 생성 완료: 차원={}", embedding.length);
      return embedding;

    } catch (Exception e) {
      log.error("임베딩 생성 실패: {}", text, e);
      throw new RuntimeException("임베딩 생성에 실패했습니다.", e);
    }
  }
} 