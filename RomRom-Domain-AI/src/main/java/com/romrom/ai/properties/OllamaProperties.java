package com.romrom.ai.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

  private boolean enabled = false;
  private String baseUrl = "https://ai.suhsaechan.kr";
  private String apiKey;
  private String chatModel = "granite4:micro-h"; // 기본값
  private String embeddingModel = "embeddinggemma:latest"; // 기본값
}
