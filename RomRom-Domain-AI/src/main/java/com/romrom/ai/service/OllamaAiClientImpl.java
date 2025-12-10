package com.romrom.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.dto.AiGenerationConfig;
import com.romrom.ai.properties.AiPromptProperties;
import com.romrom.ai.properties.AiPromptProperties.GenerationConfig;
import com.romrom.ai.properties.OllamaProperties;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("ollamaAiClient")
@Slf4j
public class OllamaAiClientImpl implements AiClient {

  private final OllamaChatModel chatModel;
  private final OllamaEmbeddingModel embeddingModel;
  private final ObjectMapper mapper;
  private final OllamaProperties ollamaProperties;
  private final AiPromptProperties aiPromptProperties;

  public OllamaAiClientImpl(
      @Qualifier("ollamaChatModel") OllamaChatModel chatModel,
      @Qualifier("ollamaEmbeddingModel") OllamaEmbeddingModel embeddingModel,
      ObjectMapper mapper,
      OllamaProperties ollamaProperties,
      AiPromptProperties aiPromptProperties
  ) {
    this.chatModel = chatModel;
    this.embeddingModel = embeddingModel;
    this.mapper = mapper;
    this.ollamaProperties = ollamaProperties;
    this.aiPromptProperties = aiPromptProperties;
  }

  @Override
  public String getClientName() {
    return "Ollama";
  }

  @Override
  public float[] generateEmbedding(String text) {
    try {
      log.debug("[Ollama] 임베딩 생성 요청: {}", text);
      EmbeddingResponse response = embeddingModel.call(
          new org.springframework.ai.embedding.EmbeddingRequest(
              List.of(text),
              OllamaEmbeddingOptions.builder()
                  .model(ollamaProperties.getEmbeddingModel())
                  .build()
          )
      );

      float[] vector = response.getResult().getOutput();
      log.debug("[Ollama] 임베딩 생성 완료: 차원={}", vector.length);
      return vector;
    } catch (Exception e) {
      log.error("[Ollama] 임베딩 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.OLLAMA_API_CALL_FAILED);
    }
  }

  @Override
  public String generateContent(String prompt) {
    return generateContent(prompt, AiGenerationConfig.defaultConfig());
  }

  @Override
  public String generateContent(String prompt, AiGenerationConfig config) {
    try {
      log.debug("[Ollama] 텍스트 생성 요청");

      OllamaChatOptions options = OllamaChatOptions.builder()
          .model(ollamaProperties.getChatModel())
          .temperature(config.temperature())
          .build();

      ChatResponse response = chatModel.call(new Prompt(prompt, options));
      String result = response.getResult().getOutput().getText();

      log.debug("[Ollama] 텍스트 생성 완료");
      return result;
    } catch (Exception e) {
      log.error("[Ollama] 텍스트 생성 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.OLLAMA_API_CALL_FAILED);
    }
  }

  @Override
  public int getItemPricePrediction(String inputText) {
    try {
      String instruction = aiPromptProperties.instruction()
          .replace("{{INPUT_TEXT}}", sanitizeForModel(inputText));

      GenerationConfig generationConfig = aiPromptProperties.generationConfig();

      // JSON 응답을 위한 프롬프트 강화
      String jsonPrompt = instruction + "\n\nIMPORTANT: Return ONLY valid JSON with format: {\"price_krw\": <integer>}";

      OllamaChatOptions options = OllamaChatOptions.builder()
          .model(ollamaProperties.getChatModel())
          .temperature(generationConfig.temperature())
          .format("json")
          .build();

      ChatResponse response = chatModel.call(new Prompt(jsonPrompt, options));
      String json = response.getResult().getOutput().getText();

      log.debug("[Ollama] 가격 예측 응답: {}", json);

      JsonNode root = mapper.readTree(json);
      JsonNode priceNode = root.get("price_krw");

      if (priceNode == null || !priceNode.isNumber()) {
        log.error("[Ollama] 가격 정보를 찾을 수 없거나 유효하지 않음: {}", json);
        throw new CustomException(ErrorCode.OLLAMA_RESPONSE_PARSE_FAILED);
      }
      return priceNode.asInt();

    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      log.error("[Ollama] 가격 예측 실패: {}", e.getMessage(), e);
      throw new CustomException(ErrorCode.OLLAMA_API_CALL_FAILED);
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
