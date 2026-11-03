package com.romrom.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 기본 ObjectMapper 설정 단일 원본: JavaTimeModule, 날짜는 숫자 대신 ISO 문자열
 * JacksonConfig 빈과 Spring 밖 정적 유틸(JsonUtil)이 같은 설정을 쓰도록 생성은 여기서만
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DefaultObjectMapperFactory {

  public static ObjectMapper createDefaultObjectMapper() {
    ObjectMapper defaultObjectMapper = new ObjectMapper();
    defaultObjectMapper.registerModule(new JavaTimeModule());
    defaultObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return defaultObjectMapper;
  }
}
