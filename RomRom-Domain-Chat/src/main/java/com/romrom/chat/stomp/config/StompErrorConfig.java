package com.romrom.chat.stomp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

@Configuration
public class StompErrorConfig {

  @Bean
  public StompSubProtocolErrorHandler stompSubProtocolErrorHandler() {
    return new StompSubProtocolErrorHandler();
  }

}
