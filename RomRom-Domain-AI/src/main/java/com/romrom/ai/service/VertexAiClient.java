package com.romrom.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class VertexAiClient {

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

  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  private String getEndpoint() {
    return "https://" + location + "-aiplatform.googleapis.com/v1/projects/"
           + projectId
           + "/locations/" + location + "/publishers/google/models/" + model + ":predict";
  }

  private String getGeminiEndpoint() {
    String geminiModel = "gemini-2.0-flash-lite-001";
    String geminiLocation = "us-central1";
    return String.format(
        "https://%s-aiplatform.googleapis.com/v1/projects/%s" +
        "/locations/%s/publishers/google/models/%s:generateContent",
        geminiLocation, projectId, geminiLocation, geminiModel
    );
  }

  public float[] getTextEmbedding(String text) {
    String requestJson = """
        {
          "instances": [{"content": "%s"}],
          "parameters": {"dimension": %d}
        }
        """.formatted(text, dimension);

    RequestBody body = RequestBody.create(
        requestJson,
        MediaType.parse("application/json")
    );

    HttpUrl url = HttpUrl.parse(getEndpoint()).newBuilder()
        .addQueryParameter("key", apiKey)
        .build();

    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer " + getAccessTokenFromServiceAccount())
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
      }

      String responseBody = response.body().string();
      JsonNode root = mapper.readTree(responseBody);
      JsonNode vectorNode = root.get("predictions").get(0).get("embeddings");

      float[] vector = new float[vectorNode.size()];
      for (int i = 0; i < vectorNode.size(); i++) {
        vector[i] = (float) vectorNode.get(i).asDouble();
      }

      return vector;

    } catch (IOException e) {
      throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
    }
  }

  public List<float[]> getTextEmbeddings(List<String> texts) {

    List<Map<String, String>> instances = texts.stream()
        .map(text -> Map.of("content", text))
        .toList();

    Map<String, Object> requestMap = Map.of(
        "instances", instances,
        "parameters", Map.of("dimension", dimension)
    );

    ObjectMapper mapper = new ObjectMapper();
    String requestJson;
    try {
      requestJson = mapper.writeValueAsString(requestMap);
    } catch (JsonProcessingException e) {
      throw new CustomException(ErrorCode.VERTEX_REQUEST_SERIALIZATION_FAILED);
    }

    RequestBody body = RequestBody.create(
        requestJson,
        MediaType.parse("application/json"));

    HttpUrl url = HttpUrl.parse(getEndpoint()).newBuilder()
        .addQueryParameter("key", apiKey)
        .build();

    Request request = new Request.Builder()
        .url(url)
        .post(body)
        .addHeader("Authorization", "Bearer " + getAccessTokenFromServiceAccount())
        .build();

    try (Response response = client.newCall(request).execute()) {
      // HTTP 요청 결과가 실패일 경우 예외 처리
      if (!response.isSuccessful()) {
        throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
      }

      // 응답 본문(JSON) 파싱
      String responseBody = response.body().string();
      JsonNode root = mapper.readTree(responseBody);

      // predictions 필드 추출 (Vertex AI 응답의 핵심 결과)
      JsonNode predictions = root.get("predictions");
      if (predictions == null || !predictions.isArray()) {
        throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MISSING);
      }

      List<float[]> result = new ArrayList<>();
      for (JsonNode predictionNode : predictions) {
        // 각 prediction에서 embeddings 필드 추출
        JsonNode embeddingNode = predictionNode.get("embeddings");

        // embeddings 또는 values 필드가 없으면 경고 로그 후 예외 처리
        if (embeddingNode == null || !embeddingNode.has("values")) {
          log.warn("'embeddings.values' 필드가 누락되었거나 잘못되었습니다. 응답 내용: {}", predictionNode.toPrettyString());
          throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MALFORMED);
        }

        // values 필드 추출 (임베딩 벡터 값들이 들어있는 배열)
        JsonNode valuesNode = embeddingNode.get("values");
        if (!valuesNode.isArray()) {
          log.warn("'values' 필드 형식이 배열이 아닙니다. 응답 내용: {}", valuesNode.toPrettyString());
          throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MALFORMED);
        }

        // 임베딩 벡터 초기화 및 값 변환
        float[] vector = new float[valuesNode.size()];
        for (int i = 0; i < valuesNode.size(); i++) {
          JsonNode valNode = valuesNode.get(i);
          if (valNode == null || !valNode.isNumber()) {
            log.warn("임베딩 값이 숫자가 아닙니다. 인덱스: {}, 값: {}", i, valNode);
            vector[i] = 0f;
          } else {
            vector[i] = (float) valNode.asDouble();
          }
        }
        log.debug("임베딩 벡터 생성 완료. 크기: {}, 첫 값: {}", vector.length, vector.length > 0 ? vector[0] : "비어있음");

        result.add(vector);
      }
      return result;

    } catch (IOException e) {
      // 응답 파싱 실패 시 예외 처리
      throw new CustomException(ErrorCode.VERTEX_RESPONSE_PARSE_FAILED);
    }
  }

  private String getAccessTokenFromServiceAccount() {
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(credentialsFile)) {

      GoogleCredentials credentials = GoogleCredentials
          .fromStream(input)
          .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));

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
      String generationConfig = """
            {
                "temperature": 0.0,
                "maxOutputTokens": 100,
                "response_mime_type": "application/json",
                "responseSchema": %s
            }
            """.formatted(schema);

      // 요청 바디 구성
      String requestJson = """
            {
                "contents": [
                    {
                        "role": "user",
                        "parts": [
                            {
                                "text": "%s"
                            }
                        ]
                    }
                ],
                "generationConfig": %s
            }
            """.formatted(prompt.replace("\"", "\\\""), generationConfig);

      log.info("Request JSON: {}", requestJson);

      RequestBody body = RequestBody.create(
          requestJson,
          MediaType.parse("application/json")
      );

      HttpUrl url = HttpUrl.parse(getGeminiEndpoint()).newBuilder()
          .build(); // API 키 제거

      log.info("Request URL: {}", url);

      Request request = new Request.Builder()
          .url(url)
          .post(body)
          .addHeader("Authorization", "Bearer " + getAccessTokenFromServiceAccount())
          .build();

      try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String errorBody = response.body().string();
          log.error("가격 예측 API 호출 실패: {}, body={}", response.code(), errorBody);
          throw new CustomException(ErrorCode.VERTEX_API_CALL_FAILED);
        }

        String responseBody = response.body().string();
        log.info("Response body: {}", responseBody);

        JsonNode root = mapper.readTree(responseBody);
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.size() == 0) {
          log.warn("candidates 필드 누락 또는 비어있음: {}", responseBody);
          throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MISSING);
        }

        JsonNode contentNode = candidates.get(0).path("content").path("parts").get(0).path("text");
        if (contentNode == null || !contentNode.isTextual()) {
          log.warn("응답에서 텍스트 콘텐츠 누락: {}", responseBody);
          throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MALFORMED);
        }

        JsonNode priceNode = mapper.readTree(contentNode.asText()).get("price_krw");
        if (priceNode == null || !priceNode.isInt()) {
          log.warn("price_krw 필드 누락 또는 숫자 아님: {}", responseBody);
          throw new CustomException(ErrorCode.VERTEX_PREDICTIONS_MALFORMED);
        }

        return priceNode.asInt();
      }
    } catch (IOException e) {
      log.error("가격 예측 요청 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.VERTEX_RESPONSE_PARSE_FAILED);
    }
  }
} 