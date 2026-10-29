package com.romrom.web.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Redis 소켓 옵션 검증 (#823): keepalive 활성 + 주기, 연결 타임아웃 유지
 */
class RedisConfigTest {

  @Test
  @DisplayName("keepalive는 유휴 60초 후 10초 간격 3회")
  void keepalive가_짧은_주기로_켜진다() {
    SocketOptions socketOptions = RedisConfig.toSocketOptions(null);

    assertTrue(socketOptions.isKeepAlive());
    assertEquals(RedisConfig.KEEPALIVE_IDLE, socketOptions.getKeepAlive().getIdle());
    assertEquals(RedisConfig.KEEPALIVE_INTERVAL, socketOptions.getKeepAlive().getInterval());
    assertEquals(RedisConfig.KEEPALIVE_PROBE_COUNT, socketOptions.getKeepAlive().getCount());
  }

  @Test
  @DisplayName("연결 타임아웃: 미설정이면 Lettuce 기본값, 설정하면 그 값")
  void 연결_타임아웃은_설정값을_따른다() {
    assertEquals(SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION, RedisConfig.toSocketOptions(null).getConnectTimeout());
    assertEquals(Duration.ofSeconds(3), RedisConfig.toSocketOptions(Duration.ofSeconds(3)).getConnectTimeout());
  }
}
