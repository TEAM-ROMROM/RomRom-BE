package com.romrom.web.config;

import io.lettuce.core.SocketOptions;
import io.lettuce.core.SocketOptions.KeepAliveOptions;
import java.time.Duration;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  // 유휴 연결 keepalive (#823): 공인 호스트 경유(NAS 포트 매핑·NAT)에서 유휴 연결이 조용히 끊겨 첫 요청이 Connection reset
  // 유휴 60초 후 10초 간격 3회 탐지 (epoll·JDK 11+ NIO에서 적용)
  static final Duration KEEPALIVE_IDLE = Duration.ofSeconds(60);
  static final Duration KEEPALIVE_INTERVAL = Duration.ofSeconds(10);
  static final int KEEPALIVE_PROBE_COUNT = 3;

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);

    // 직렬화 설정
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

    return template;
  }

  /**
   * Lettuce 소켓 옵션: keepalive 추가 (Boot 기본 ClientOptions 위에 적용, connect-timeout 설정 유지)
   */
  @Bean
  public LettuceClientOptionsBuilderCustomizer redisKeepAliveCustomizer(RedisProperties redisProperties) {
    return clientOptionsBuilder -> clientOptionsBuilder.socketOptions(toSocketOptions(redisProperties.getConnectTimeout()));
  }

  /**
   * 연결 타임아웃(미설정 시 Lettuce 기본) + keepalive > SocketOptions
   */
  static SocketOptions toSocketOptions(Duration connectTimeout) {
    SocketOptions.Builder socketOptionsBuilder = SocketOptions.builder()
        .keepAlive(KeepAliveOptions.builder()
            .enable()
            .idle(KEEPALIVE_IDLE)
            .interval(KEEPALIVE_INTERVAL)
            .count(KEEPALIVE_PROBE_COUNT)
            .build());
    if (connectTimeout != null) {
      socketOptionsBuilder.connectTimeout(connectTimeout);
    }
    return socketOptionsBuilder.build();
  }
}
