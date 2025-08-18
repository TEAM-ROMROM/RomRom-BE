package com.romrom.chat.stomp.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging.broker.amqp")
public record RabbitMqProperties(
    String host,
    int port,
    String virtualHost,
    String username,
    String password
) {

}
