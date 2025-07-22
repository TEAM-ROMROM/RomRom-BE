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

  @Value("${vertex.ai.location}")
  private String location;

  @Value("${vertex.ai.credentials-file}")
  private String credentialsFile;

  @Value("${vertex.ai.use-vertex-ai}")
  private Boolean useVertexAi;

  @Value("${vertex.ai.model}")
  private String model;

  @Value("${vertex.ai.cloud-platform-url}")
  private String cloudPlatformUrl;

  @Bean
  public Client genAiClient() throws IOException {
    // JSON 파일 로드
    ClassPathResource classPathResource = new ClassPathResource(credentialsFile);
    try (InputStream inputStream = classPathResource.getInputStream()) {

      // 서비스 계정 로드
      GoogleCredentials googleCredentials =
          ServiceAccountCredentials.fromStream(inputStream)
              .createScoped(List.of(cloudPlatformUrl));

      return Client.builder()
          .vertexAI(useVertexAi)
          .project(projectId)
          .location(location)
          .credentials(googleCredentials)
          .build();
    }
  }
}
