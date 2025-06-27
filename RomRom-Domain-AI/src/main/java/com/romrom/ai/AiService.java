package com.romrom.ai;

import com.romrom.item.dto.ItemRequest;
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

  /**
   * 제품 설명을 기반으로 중고 거래 가격 예측
   *
   * @param itemRequest 제품 설명 요청 객체
   * @return 예측된 가격 (KRW, 정수)
   */
  public int predictItemPrice(ItemRequest itemRequest) {
    try {
      // 필요한 정보만 추출해서 텍스트 생성
      String itemName = itemRequest.getItemName();
      String description = itemRequest.getItemDescription();
      String condition = itemRequest.getItemCondition() != null ? itemRequest.getItemCondition().name() : "";

      // Vertex AI에 보낼 문장 조합
      StringBuilder promptBuilder = new StringBuilder();
      if (itemName != null) promptBuilder.append(itemName).append(", ");
      if (description != null) promptBuilder.append(description).append(", ");
      if (!condition.isEmpty()) promptBuilder.append("상태: ").append(condition);

      String prompt = promptBuilder.toString();

      log.info("중고가 예측 요청 문장: {}", prompt);
      return vertexAiClient.getItemPricePrediction(prompt);

    } catch (Exception e) {
      log.error("가격 예측 실패: {}", itemRequest, e);
      throw new RuntimeException("가격 예측 실패", e);
    }
  }
} 