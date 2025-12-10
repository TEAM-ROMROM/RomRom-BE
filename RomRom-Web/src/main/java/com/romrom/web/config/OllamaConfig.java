package com.romrom.web.config;

import com.romrom.ai.properties.OllamaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Ollama AI 설정
 * 커스텀 헤더(X-API-Key)를 포함한 RestClient 구성
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class OllamaConfig {

  private final OllamaProperties ollamaProperties;

  @Bean
  public OllamaApi ollamaApi() {
    log.debug("[OllamaConfig] OllamaApi 생성 - baseUrl: {}", ollamaProperties.getBaseUrl());

    RestClient.Builder builder = RestClient.builder()
        .baseUrl(ollamaProperties.getBaseUrl());

    // API Key가 설정되어 있으면 헤더 추가
    if (ollamaProperties.getApiKey() != null && !ollamaProperties.getApiKey().isBlank()) {
      ClientHttpRequestInterceptor apiKeyInterceptor = (request, body, execution) -> {
        request.getHeaders().add("X-API-Key", ollamaProperties.getApiKey());
        return execution.execute(request, body);
      };
      builder.requestInterceptor(apiKeyInterceptor);
      log.debug("[OllamaConfig] X-API-Key 헤더 추가됨");
    }

    return new OllamaApi(ollamaProperties.getBaseUrl(), builder);
  }

  @Bean("ollamaChatModel")
  public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
    log.debug("[OllamaConfig] OllamaChatModel 생성 - model: {}", ollamaProperties.getChatModel());

    return OllamaChatModel.builder()
        .ollamaApi(ollamaApi)
        .defaultOptions(OllamaOptions.builder()
            .model(ollamaProperties.getChatModel())
            .build())
        .build();
  }

  @Bean("ollamaEmbeddingModel")
  public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi) {
    log.debug("[OllamaConfig] OllamaEmbeddingModel 생성 - model: {}", ollamaProperties.getEmbeddingModel());

    return OllamaEmbeddingModel.builder()
        .ollamaApi(ollamaApi)
        .defaultOptions(OllamaOptions.builder()
            .model(ollamaProperties.getEmbeddingModel())
            .build())
        .build();
  }
}
