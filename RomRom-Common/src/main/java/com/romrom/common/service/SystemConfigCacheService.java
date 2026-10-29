package com.romrom.common.service;

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

  public String get(String configKey) {
    Object value = redisTemplate.opsForHash().get(CACHE_KEY, configKey);
    return value != null ? value.toString() : null;
  }

  public String getOrDefault(String configKey, String defaultValue) {
    String value = get(configKey);
    return value != null ? value : defaultValue;
  }

  /**
   * 양의 정수 설정 조회: 없음·빈 값 > 기본값, 숫자 아님·0 이하 > 경고 로그 후 기본값
   */
  public int getPositiveIntOrDefault(String configKey, int defaultValue) {
    String configValue = get(configKey);
    if (configValue == null || configValue.isBlank()) {
      return defaultValue;
    }
    try {
      int parsedConfigValue = Integer.parseInt(configValue.trim());
      if (parsedConfigValue > 0) {
        return parsedConfigValue;
      }
    } catch (NumberFormatException numberFormatException) {
      // 아래 경고 로그와 기본값 반환으로 처리
    }
    log.warn("[system config] invalid positive number : key={}, value={}, default={}", configKey, configValue, defaultValue);
    return defaultValue;
  }

  public void put(String configKey, String configValue) {
    redisTemplate.opsForHash().put(CACHE_KEY, configKey, configValue);
  }

  public void putAll(Map<String, String> configMap) {
    redisTemplate.delete(CACHE_KEY);
    if (!configMap.isEmpty()) {
      redisTemplate.opsForHash().putAll(CACHE_KEY, new HashMap<>(configMap));
    }
    log.info("시스템 설정 캐시 전체 로딩 완료: {} 건", configMap.size());
  }

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

  public void clearAll() {
    redisTemplate.delete(CACHE_KEY);
    log.info("시스템 설정 캐시 전체 삭제 완료");
  }
}
