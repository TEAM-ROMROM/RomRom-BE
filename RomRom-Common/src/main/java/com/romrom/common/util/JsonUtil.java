package com.romrom.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class JsonUtil {

  private static final ObjectMapper MAPPER = new ObjectMapper();

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
      log.error("Map -> JSON 문자열 변환에 실패했습니다: 요청Map: {}", map);
      throw new CustomException(ErrorCode.CONVERT_JSON_FAILED);
    }
  }
}
