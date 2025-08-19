package com.romrom.chat.stomp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging.routing")
public record ChatRoutingProperties(
    String chatExchange,
    String chatQueue,
    String unreadRoutingKeyPrefix
) {

}
