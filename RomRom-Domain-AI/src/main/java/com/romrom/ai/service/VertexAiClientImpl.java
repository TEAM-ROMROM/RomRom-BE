package com.romrom.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.types.*;
import com.romrom.ai.VertexAiProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
public class VertexAiClientImpl implements VertexAiClient {

  private final Client embeddingClient;
  private final Client generationClient;
  private final ObjectMapper mapper;
  private final VertexAiProperties vertexAiProperties;


  public VertexAiClientImpl(
      @Qualifier("embeddingClient") Client embeddingClient,
      @Qualifier("generationClient") Client generationClient,
      ObjectMapper mapper, VertexAiProperties vertexAiProperties
  ) {
    this.embeddingClient = embeddingClient;
    this.generationClient = generationClient;
    this.mapper = mapper;
    this.vertexAiProperties = vertexAiProperties;
  }

  // 임베딩 AI 모델로 임베딩 생성 메서드
  @Override
  public EmbedContentResponse generateEmbedding(String text) {
    try {
      return embeddingClient.models.embedContent(vertexAiProperties.getEmbeddingModel(), text, EmbedContentConfig.builder().build());
    }
    catch (ClientException e) {
      log.error("임베딩 AI 모델로 임베딩 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // 생성형 AI 모델로 답변 생성 메서드
  @Override
  public GenerateContentResponse generateContent(String text) {
    try {
      return generationClient.models.generateContent(vertexAiProperties.getGenerationModel(), text, GenerateContentConfig.builder().build());
    }
    catch (ClientException e) {
      log.error("생성형 AI 모델로 답변 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // 생성형 AI 모델로 답변 생성 메서드 (설정 포함)
  @Override
  public GenerateContentResponse generateContent(String text, GenerateContentConfig config) {
    try {
      return generationClient.models.generateContent(vertexAiProperties.getGenerationModel(), text, config);
    }
    catch (ClientException e) {
      log.error("생성형 AI 모델로 답변 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  @Override
  public int getItemPricePrediction(String inputText) {
    try {
      // 프롬프트 구성
      String prompt = """
          %s의 중고 거래 예상 가격을 한국 원화(KRW)로 숫자만 반환해 줘.
          응답 예시: 600000
          """.formatted(inputText.replace("\"", "\\\""));

      // JSON 스키마 정의
      String schema = """
          {
              "type": "object",
              "properties": {
                  "price_krw": {
                      "type": "integer"
                  }
              },
              "required": ["price_krw"]
          }
          """;

      // GenerationConfig 구성
      GenerateContentConfig config = GenerateContentConfig.builder()
          .temperature(0.0F)                      // 결정론적 응답
          .maxOutputTokens(50)                    // 최대 토큰 수
          .responseMimeType("application/json")   // JSON 모드로 응답
          .responseSchema(Schema.fromJson(schema))    // 위에서 정의한 스키마
          .build();

      // SDK 호출
      GenerateContentResponse response = generateContent(prompt, config);

      // 응답 텍스트(JSON)를 파싱하여 price_krw 반환
      String json = response.text();
      JsonNode root = mapper.readTree(json);
      JsonNode priceNode = root.get("price_krw");

      if (priceNode == null || !priceNode.isNumber()) {
        log.error("가격 정보를 찾을 수 없거나 유효하지 않음: {}", json);
        throw new CustomException(ErrorCode.VERTEX_RESPONSE_PARSE_FAILED);
      }
      return priceNode.asInt();

    } catch (IOException e) {
      log.error("응답 파싱 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_RESPONSE_PARSE_FAILED);
    }
    catch (ClientException e) {
      log.error("Vertex AI API 호출 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }
}