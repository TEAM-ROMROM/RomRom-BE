package com.romrom.chat.stomp.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging.websocket")
public record WebSocketProperties(
    String endpointPath,
    List<String> allowedOrigins,
    String appDestinationPrefix,
    String userDestinationPrefix,
    String subscribeAliasPrefix,
    boolean pathMatcherDotEnabled,
    String errorDestination
) {

}
