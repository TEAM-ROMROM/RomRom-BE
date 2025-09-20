package com.romrom.chat.stomp.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.routing")
public class ChatRoutingProperties {
  private String chatExchange;
  private String chatQueue;
  private String unreadRoutingKeyPrefix;
}