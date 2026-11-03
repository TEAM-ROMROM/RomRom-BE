package com.romrom.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 기본 ObjectMapper 원본·JsonUtil 단위 테스트: 날짜 ISO 문자열, Map 왕복, 빈 값·깨진 JSON 처리
 */
class JsonUtilTest {

  @Test
  @DisplayName("기본 ObjectMapper는 LocalDateTime을 숫자 배열이 아닌 ISO 문자열로 쓴다")
  void 기본_ObjectMapper_날짜_ISO() throws Exception {
    ObjectMapper defaultObjectMapper = DefaultObjectMapperFactory.createDefaultObjectMapper();
    Assertions.assertEquals("\"2026-11-03T12:30:00\"",
        defaultObjectMapper.writeValueAsString(LocalDateTime.of(2026, 11, 3, 12, 30)));
  }

  @Test
  @DisplayName("Map > JSON > Map 왕복 시 값이 같다")
  void Map_왕복() {
    Map<String, String> notificationPayload = Map.of("notificationType", "CHAT_MESSAGE", "chatRoomId", "room-1");
    Assertions.assertEquals(notificationPayload, JsonUtil.convertToStringMap(JsonUtil.convertToJson(notificationPayload)));
  }

  @Test
  @DisplayName("null·빈 문자열은 빈 Map, 빈 Map은 {}")
  void 빈_값_처리() {
    Assertions.assertTrue(JsonUtil.convertToStringMap(null).isEmpty());
    Assertions.assertTrue(JsonUtil.convertToStringMap("").isEmpty());
    Assertions.assertEquals("{}", JsonUtil.convertToJson(Map.of()));
  }

  @Test
  @DisplayName("깨진 JSON은 CONVERT_JSON_FAILED")
  void 깨진_JSON() {
    CustomException exception = Assertions.assertThrows(CustomException.class, () -> JsonUtil.convertToStringMap("{broken"));
    Assertions.assertEquals(ErrorCode.CONVERT_JSON_FAILED, exception.getErrorCode());
  }
}
