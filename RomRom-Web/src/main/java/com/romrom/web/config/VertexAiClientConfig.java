package com.romrom.web.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import com.romrom.ai.VertexAiProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
@Getter
@RequiredArgsConstructor
public class VertexAiClientConfig {

  private final VertexAiProperties vertexAiProperties;

  @Bean("embeddingClient")
  public Client embeddingClient() throws IOException {
    return createClientForLocation(vertexAiProperties.getEmbeddingLocation());
  }

  @Bean("generationClient")
  public Client generationClient() throws IOException {
    return createClientForLocation(vertexAiProperties.getGenerationLocation());
  }

  private Client createClientForLocation(String location) throws IOException {
    // JSON 파일 로드 (try-with-resources 사용)
    try (InputStream inputStream = new ClassPathResource(vertexAiProperties.getCredentialsFile()).getInputStream()) {
      GoogleCredentials credentials = ServiceAccountCredentials
          .fromStream(inputStream)
          .createScoped(List.of(vertexAiProperties.getCloudPlatformUrl()));

      return Client.builder()
          .vertexAI(vertexAiProperties.getUseVertexAi())
          .project(vertexAiProperties.getProjectId())
          .location(location)
          .credentials(credentials)
          .build();
    }
  }
}
