package com.romrom.chat.stomp.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging.broker.stomp")
public record StompRelayProperties(
    String host,
    int port,
    String virtualHost,
    String username,
    String password,
    List<String> relayDestinations
) {

}
