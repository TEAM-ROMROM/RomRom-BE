package com.romrom.ai.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "suh.aider")
public class SuhAiderProperties {

  private String baseUrl;
  private Security security;
  private Embedding embedding;

  @Getter
  @Setter
  public static class Security {
    private String apiKey;
  }

  @Getter
  @Setter
  public static class Embedding {
    private String defaultModel;
  }
}
