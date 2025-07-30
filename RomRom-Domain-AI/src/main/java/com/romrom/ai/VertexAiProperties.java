package com.romrom.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "vertex.ai")
public class VertexAiProperties {

  private String projectId;
  private String credentialsFile;
  private int dimension;
  private Boolean useVertexAi;
  private String cloudPlatformUrl;

  private String embeddingLocation;
  private String embeddingModel;

  private String generationLocation;
  private String generationModel;
}
