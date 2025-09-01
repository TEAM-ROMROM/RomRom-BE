package com.romrom.chat.stomp.properties;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.broker.stomp")
public class StompRelayProperties {
  private String host;
  private int port;
  private String virtualHost;
  private String username;
  private String password;
  private List<String> relayDestinations;
}