package com.romrom.chat.stomp.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.broker.amqp")
public class RabbitMqProperties {
  private String host;
  private int port;
  private String virtualHost;
  private String username;
  private String password;
}
