package com.romrom.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class JsonUtil {

  // Spring 밖 정적 호출용 (설정은 JacksonConfig 빈과 같은 원본)
  private static final ObjectMapper MAPPER = DefaultObjectMapperFactory.createDefaultObjectMapper();
  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE_REFERENCE = new TypeReference<>() {};

  /**
   * Map -> JSON 문자열
   */
  public String convertToJson(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return "{}";
    }
    try {
      return MAPPER.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      log.error("Map -> JSON 문자열 변환에 실패했습니다: 요청Map: {}", map, e);
      throw new CustomException(ErrorCode.CONVERT_JSON_FAILED);
    }
  }

  /**
   * JSON 문자열 > Map (비어 있으면 빈 Map)
   */
  public Map<String, String> convertToStringMap(String json) {
    if (json == null || json.isEmpty()) {
      return new HashMap<>();
    }
    try {
      return MAPPER.readValue(json, STRING_MAP_TYPE_REFERENCE);
    } catch (JsonProcessingException e) {
      log.error("JSON 문자열 -> Map 변환에 실패했습니다: 길이={}", json.length(), e);
      throw new CustomException(ErrorCode.CONVERT_JSON_FAILED);
    }
  }
}
