package com.romrom.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.util.DefaultObjectMapperFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    return DefaultObjectMapperFactory.createDefaultObjectMapper();
  }

}
