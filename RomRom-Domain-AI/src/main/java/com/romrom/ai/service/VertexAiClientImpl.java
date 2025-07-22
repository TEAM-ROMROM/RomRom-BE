package com.romrom.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.types.*;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VertexAiClientImpl implements VertexAiClient {

  private final Client genAiClient;

  @Value("${vertex.ai.api-key}")
  private String apiKey;

  @Value("${vertex.ai.project-id}")
  private String projectId;

  @Value("${vertex.ai.location}")
  private String location;

  @Value("${vertex.ai.model}")
  private String model;

  @Value("${vertex.ai.dimension}")
  private int dimension;

  @Value("${vertex.ai.credentials-file}")
  private String credentialsFile;

  @Value("${vertex.ai.cloud-platform-url}")
  private String cloudPlatformUrl;

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public EmbedContentResponse generateEmbedding(String text) {
    try {
      return genAiClient.models.embedContent(model, text, EmbedContentConfig.builder().build());
    }
    catch (ClientException e) {
      log.error("embed 메서드 실행 중 오류 발생 : {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  private String getAccessTokenFromServiceAccount() {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(credentialsFile)) {

      GoogleCredentials credentials = GoogleCredentials
          .fromStream(input)
          .createScoped(List.of(cloudPlatformUrl));

      credentials.refreshIfExpired();
      return credentials.getAccessToken().getTokenValue();

    } catch (IOException e) {
      log.error("Failed to load credentials: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_AUTH_TOKEN_FAILED);
    }
  }

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
      GenerateContentResponse response = genAiClient.models.generateContent(model, prompt, config);

      // 응답 텍스트(JSON)를 파싱하여 price_krw 반환
      String json = response.text();
      JsonNode root = mapper.readTree(json);
      return root.get("price_krw").asInt();

    } catch (IOException | ClientException e) {
      log.error("가격 예측 요청 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_RESPONSE_PARSE_FAILED);
    }
  }
}