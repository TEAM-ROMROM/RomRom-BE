package com.romrom.web.service;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigCacheService {

  private static final String CACHE_KEY = "system:config";

  private final RedisTemplate<String, Object> redisTemplate;

  /**
   * Redis Hash에서 설정 값 조회
   */
  public String get(String configKey) {
    Object value = redisTemplate.opsForHash().get(CACHE_KEY, configKey);
    return value != null ? value.toString() : null;
  }

  /**
   * Redis Hash에서 설정 값 조회 (기본값 포함)
   */
  public String getOrDefault(String configKey, String defaultValue) {
    String value = get(configKey);
    return value != null ? value : defaultValue;
  }

  /**
   * Redis Hash에 설정 값 저장
   */
  public void put(String configKey, String configValue) {
    redisTemplate.opsForHash().put(CACHE_KEY, configKey, configValue);
  }

  /**
   * Redis Hash에 전체 설정 일괄 저장 (기존 캐시 교체)
   */
  public void putAll(Map<String, String> configMap) {
    redisTemplate.delete(CACHE_KEY);
    if (!configMap.isEmpty()) {
      redisTemplate.opsForHash().putAll(CACHE_KEY, new HashMap<>(configMap));
    }
    log.info("시스템 설정 캐시 전체 로딩 완료: {} 건", configMap.size());
  }

  /**
   * 특정 prefix로 시작하는 설정 값들 조회
   */
  public Map<String, String> getByPrefix(String prefix) {
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(CACHE_KEY);
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<Object, Object> entry : entries.entrySet()) {
      String key = entry.getKey().toString();
      if (key.startsWith(prefix)) {
        result.put(key, entry.getValue().toString());
      }
    }
    return result;
  }

  /**
   * 전체 캐시 삭제
   */
  public void clearAll() {
    redisTemplate.delete(CACHE_KEY);
    log.info("시스템 설정 캐시 전체 삭제 완료");
  }
}
