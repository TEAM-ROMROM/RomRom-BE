package com.romrom.ai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.genai.Client;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Configuration
@Getter
public class VertexAiClientConfig {

  @Value("${vertex.ai.project-id}")
  private String projectId;

  @Value("${vertex.ai.embedding-location}")
  private String embeddingLocation;

  @Value("${vertex.ai.generation-location}")
  private String generationLocation;

  @Value("${vertex.ai.credentials-file}")
  private String credentialsFile;

  @Value("${vertex.ai.use-vertex-ai}")
  private Boolean useVertexAi;

  @Value("${vertex.ai.cloud-platform-url}")
  private String cloudPlatformUrl;

  @Bean("embeddingClient")
  public Client embeddingClient() throws IOException {
    return createClientForLocation(embeddingLocation);
  }

  @Bean("generationClient")
  public Client generationClient() throws IOException {
    return createClientForLocation(generationLocation);
  }

  private Client createClientForLocation(String location) throws IOException {
    // JSON 파일 로드 (try-with-resources 사용)
    try (InputStream inputStream = new ClassPathResource(credentialsFile).getInputStream()) {
      GoogleCredentials credentials = ServiceAccountCredentials
          .fromStream(inputStream)
          .createScoped(List.of(cloudPlatformUrl));

      return Client.builder()
          .vertexAI(useVertexAi)
          .project(projectId)
          .location(location)
          .credentials(credentials)
          .build();
    }
  }
}
