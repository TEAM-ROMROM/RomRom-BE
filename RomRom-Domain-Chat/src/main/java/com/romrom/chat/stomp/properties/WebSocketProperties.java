package com.romrom.chat.stomp.properties;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.websocket")
public class WebSocketProperties {
  private String endpointPath;
  private List<String> allowedOrigins;
  private String appDestinationPrefix;
  private String userDestinationPrefix;
  private String subscribeAliasPrefix;
  private boolean pathMatcherDotEnabled;
  private String errorDestination;
}
