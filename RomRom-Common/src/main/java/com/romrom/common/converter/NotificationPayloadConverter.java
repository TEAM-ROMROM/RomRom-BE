package com.romrom.common.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.exception.CustomException;
import com.romrom.common.exception.ErrorCode;
import com.romrom.common.util.CommonUtil;
import com.romrom.common.util.JsonUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;

@Converter(autoApply = true)
public class NotificationPayloadConverter implements AttributeConverter<Map<String, String>, String> {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String convertToDatabaseColumn(Map<String, String> map) {
    return JsonUtil.convertToJson(map);
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String dbData) {
    if (!CommonUtil.nvl(dbData, "").isEmpty()) {
      return new HashMap<>();
    }
    try {
      return MAPPER.readValue(dbData, Map.class);
    } catch (JsonProcessingException e) {
      throw new CustomException(ErrorCode.CONVERT_JSON_FAILED);
    }
  }
}
