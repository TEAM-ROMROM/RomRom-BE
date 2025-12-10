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
import com.romrom.ai.EmbeddingUtil;
import com.romrom.ai.dto.AiGenerationConfig;
import com.romrom.ai.properties.AiPromptProperties;
import com.romrom.ai.properties.AiPromptProperties.GenerationConfig;
import com.romrom.ai.properties.VertexAiProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("vertexAiClient")
@Slf4j
public class VertexAiClientImpl implements VertexAiClient, AiClient {

  private final Client embeddingClient;
  private final Client generationClient;
  private final ObjectMapper mapper;
  private final VertexAiProperties vertexAiProperties;
  private final AiPromptProperties aiPromptProperties;

  public VertexAiClientImpl(
      @Qualifier("embeddingClient") Client embeddingClient,
      @Qualifier("generationClient") Client generationClient,
      ObjectMapper mapper,
      VertexAiProperties vertexAiProperties,
      AiPromptProperties aiPromptProperties
  ) {
    this.embeddingClient = embeddingClient;
    this.generationClient = generationClient;
    this.mapper = mapper;
    this.vertexAiProperties = vertexAiProperties;
    this.aiPromptProperties = aiPromptProperties;
  }

  @Override
  public String getClientName() {
    return "VertexAI";
  }

  // AiClient 인터페이스 구현 - 임베딩 (float[] 반환)
  @Override
  public float[] generateEmbedding(String text) {
    try {
      log.debug("[VertexAI] 임베딩 생성 요청: {}", text);
      EmbedContentResponse response = embeddingClient.models.embedContent(
          vertexAiProperties.getEmbeddingModel(), text, EmbedContentConfig.builder().build());
      float[] vector = EmbeddingUtil.extractVector(response);
      log.debug("[VertexAI] 임베딩 생성 완료: 차원={}", vector.length);
      return vector;
    } catch (ClientException e) {
      log.error("[VertexAI] 임베딩 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // VertexAiClient 인터페이스 구현 - 원본 응답 반환
  @Override
  public EmbedContentResponse generateEmbeddingResponse(String text) {
    try {
      return embeddingClient.models.embedContent(
          vertexAiProperties.getEmbeddingModel(), text, EmbedContentConfig.builder().build());
    } catch (ClientException e) {
      log.error("[VertexAI] 임베딩 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // AiClient 인터페이스 구현 - 텍스트 생성 (String 반환)
  @Override
  public String generateContent(String prompt) {
    try {
      log.debug("[VertexAI] 텍스트 생성 요청");
      GenerateContentResponse response = generationClient.models.generateContent(
          vertexAiProperties.getGenerationModel(), prompt, GenerateContentConfig.builder().build());
      log.debug("[VertexAI] 텍스트 생성 완료");
      return response.text();
    } catch (ClientException e) {
      log.error("[VertexAI] 텍스트 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // AiClient 인터페이스 구현 - 텍스트 생성 (설정 포함)
  @Override
  public String generateContent(String prompt, AiGenerationConfig config) {
    try {
      log.debug("[VertexAI] 텍스트 생성 요청 (설정 포함)");
      GenerateContentConfig vertexConfig = GenerateContentConfig.builder()
          .temperature(config.temperature())
          .maxOutputTokens(config.maxOutputTokens())
          .responseMimeType(config.responseMimeType())
          .build();

      if (config.responseSchema() != null) {
        vertexConfig = GenerateContentConfig.builder()
            .temperature(config.temperature())
            .maxOutputTokens(config.maxOutputTokens())
            .responseMimeType(config.responseMimeType())
            .responseSchema(Schema.fromJson(config.responseSchema()))
            .build();
      }

      GenerateContentResponse response = generationClient.models.generateContent(
          vertexAiProperties.getGenerationModel(), prompt, vertexConfig);
      log.debug("[VertexAI] 텍스트 생성 완료");
      return response.text();
    } catch (ClientException e) {
      log.error("[VertexAI] 텍스트 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // VertexAiClient 인터페이스 구현 - 원본 응답 반환
  @Override
  public GenerateContentResponse generateContentResponse(String text) {
    try {
      return generationClient.models.generateContent(
          vertexAiProperties.getGenerationModel(), text, GenerateContentConfig.builder().build());
    } catch (ClientException e) {
      log.error("[VertexAI] 텍스트 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  // VertexAiClient 인터페이스 구현 - 원본 응답 반환 (설정 포함)
  @Override
  public GenerateContentResponse generateContentResponse(String text, GenerateContentConfig config) {
    try {
      return generationClient.models.generateContent(
          vertexAiProperties.getGenerationModel(), text, config);
    } catch (ClientException e) {
      log.error("[VertexAI] 텍스트 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  @Override
  public int getItemPricePrediction(String inputText) {
    try {
      String instruction = aiPromptProperties.instruction()
          .replace("{{INPUT_TEXT}}", sanitizeForModel(inputText));

      String schemaJson = aiPromptProperties.responseSchemaJson();
      GenerationConfig generationConfig = aiPromptProperties.generationConfig();

      // GenerationConfig 구성
      GenerateContentConfig config = GenerateContentConfig.builder()
          .temperature(generationConfig.temperature()) // 결정론적 응답
          .maxOutputTokens(generationConfig.maxOutputTokens()) // 최대 토큰 수
          .responseMimeType(generationConfig.responseMimeType()) // JSON 모드로 응답
          .responseSchema(Schema.fromJson(schemaJson)) // 위에서 정의한 스키마
          .build();

      // SDK 호출
      GenerateContentResponse response = generateContentResponse(instruction, config);

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
}