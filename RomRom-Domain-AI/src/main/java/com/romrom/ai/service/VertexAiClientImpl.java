package com.romrom.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.romrom.ai.properties.AiPromptProperties;
import com.romrom.ai.properties.AiPromptProperties.GenerationConfig;
import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.service.SystemConfigCacheService;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VertexAiClientImpl implements VertexAiClient {

  // 가격 예측 프롬프트 관련 system_config 키 (관리자 페이지에서 런타임 수정 가능)
  private static final String PRICE_PREDICTION_INSTRUCTION_CONFIG_KEY = "ai.prompt.price-prediction.instruction";

  private final Client embeddingClient;
  private final Client generationClient;
  private final ObjectMapper mapper;
  private final VertexAiProperties vertexAiProperties;
  private final AiPromptProperties aiPromptProperties;
  private final SystemConfigCacheService systemConfigCacheService;

  public VertexAiClientImpl(
      @Qualifier("embeddingClient") Client embeddingClient,
      @Qualifier("generationClient") Client generationClient,
      ObjectMapper mapper,
      VertexAiProperties vertexAiProperties,
      AiPromptProperties aiPromptProperties,
      SystemConfigCacheService systemConfigCacheService
  ) {
    this.embeddingClient = embeddingClient;
    this.generationClient = generationClient;
    this.mapper = mapper;
    this.vertexAiProperties = vertexAiProperties;
    this.aiPromptProperties = aiPromptProperties;
    this.systemConfigCacheService = systemConfigCacheService;
  }

  // 임베딩 AI 모델로 임베딩 생성 메서드
  @Override
  public EmbedContentResponse generateEmbedding(String text) {
    try {
      return embeddingClient.models.embedContent(vertexAiProperties.getEmbeddingModel(), text, EmbedContentConfig.builder().build());
    } catch (ClientException e) {
      log.error("임베딩 AI 모델로 임베딩 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // 생성형 AI 모델로 답변 생성 메서드
  @Override
  public GenerateContentResponse generateContent(String text) {
    try {
      return generationClient.models.generateContent(vertexAiProperties.getGenerationModel(), text, GenerateContentConfig.builder().build());
    } catch (ClientException e) {
      log.error("생성형 AI 모델로 답변 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // 생성형 AI 모델로 답변 생성 메서드 (설정 포함)
  @Override
  public GenerateContentResponse generateContent(String text, GenerateContentConfig config) {
    try {
      return generationClient.models.generateContent(vertexAiProperties.getGenerationModel(), text, config);
    } catch (ClientException e) {
      log.error("생성형 AI 모델로 답변 생성 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  @Override
  public int getItemPricePrediction(String inputText) {
    try {
      // 1) 관리자 페이지에서 수정 가능한 런타임 instruction 을 우선 조회, 값이 없으면 yml 기본값(AiPromptProperties) 사용
      String instructionTemplate = resolvePricePredictionInstructionTemplate();

      // 2) 입력 텍스트를 치환하여 최종 instruction 생성
      String instruction = instructionTemplate.replace("{{INPUT_TEXT}}", sanitizeForModel(inputText));

      GenerationConfig generationConfig = aiPromptProperties.generationConfig();

      // GenerationConfig 구성 (responseSchema 는 yml 고정 - 관리자 관리 대상 아님)
      GenerateContentConfig config = GenerateContentConfig.builder()
          .temperature(generationConfig.temperature()) // 결정론적 응답
          .maxOutputTokens(generationConfig.maxOutputTokens()) // 최대 토큰 수
          .responseMimeType(generationConfig.responseMimeType()) // JSON 모드로 응답
          .responseSchema(Schema.fromJson(aiPromptProperties.responseSchemaJson())) // yml 에 정의된 스키마
          .build();

      // SDK 호출
      GenerateContentResponse response = generateContent(instruction, config);

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
    } catch (ClientException e) {
      log.error("Vertex AI API 호출 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  private String sanitizeForModel(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\r", " ")
        .replace("\n", " ")
        .replace("\"", "\\\"");
  }

  // 관리자 페이지에서 지정한 instruction 이 있으면 사용, 없거나 비어 있으면 yml 기본값으로 fallback
  private String resolvePricePredictionInstructionTemplate() {
    String overriddenInstruction = systemConfigCacheService.get(PRICE_PREDICTION_INSTRUCTION_CONFIG_KEY);
    if (overriddenInstruction != null && !overriddenInstruction.isBlank()) {
      return overriddenInstruction;
    }
    return aiPromptProperties.instruction();
  }
}