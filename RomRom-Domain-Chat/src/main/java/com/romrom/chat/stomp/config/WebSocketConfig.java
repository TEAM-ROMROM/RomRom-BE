package com.romrom.chat.stomp.config;

import com.romrom.chat.stomp.interceptor.CustomChannelInterceptor;
import com.romrom.chat.stomp.properties.StompRelayProperties;
import com.romrom.chat.stomp.properties.WebSocketProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
@EnableConfigurationProperties({
    WebSocketProperties.class,
    StompRelayProperties.class
})
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketProperties webSocketProperties;
  private final StompRelayProperties stompRelayProperties;
  private final CustomChannelInterceptor customChannelInterceptor;

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {

    // 클라이언트 -> 서버 메시지 전송 prefix
    registry.setApplicationDestinationPrefixes(webSocketProperties.getAppDestinationPrefix());

    // RabbitMQ STOMP 릴레이 설정
    registry.enableStompBrokerRelay(stompRelayProperties.getRelayDestinations().toArray(String[]::new))
        .setRelayHost(stompRelayProperties.getHost())
        .setRelayPort(stompRelayProperties.getPort())
        .setVirtualHost(stompRelayProperties.getVirtualHost())
        .setClientLogin(stompRelayProperties.getUsername())
        .setClientPasscode(stompRelayProperties.getPassword())
        .setSystemLogin(stompRelayProperties.getUsername())
        .setSystemPasscode(stompRelayProperties.getPassword());

    registry.setPathMatcher(new AntPathMatcher("."));
    registry.setUserDestinationPrefix(webSocketProperties.getUserDestinationPrefix());
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 웹소켓 연결을 위한 엔드포인트 등록 및 SockJS 폴백 지원
    registry.addEndpoint(webSocketProperties.getEndpointPath())
        .setAllowedOriginPatterns(webSocketProperties.getAllowedOrigins().toArray(String[]::new))
        .withSockJS();
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    // 클라이언트 -> 서버 (CONNECT/SUBSCRIBE/SEND 등)
    registration.interceptors(customChannelInterceptor);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    // 서버(브로커) -> 클라이언트 (MESSAGE 등)
    registration.interceptors(customChannelInterceptor);
  }
}
