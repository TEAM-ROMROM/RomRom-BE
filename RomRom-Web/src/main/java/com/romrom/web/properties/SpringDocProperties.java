package com.romrom.web.properties;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "springdoc")
public class SpringDocProperties {

  private List<Server> servers;

  @Getter
  @AllArgsConstructor
  public static class Server {
    private String url;
    private String description;
  }
}
