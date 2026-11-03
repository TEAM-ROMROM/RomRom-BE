package com.romrom.common.converter;

import com.romrom.common.util.JsonUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter(autoApply = true)
public class NotificationPayloadConverter implements AttributeConverter<Map<String, String>, String> {

  @Override
  public String convertToDatabaseColumn(Map<String, String> map) {
    return JsonUtil.convertToJson(map);
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String dbData) {
    return JsonUtil.convertToStringMap(dbData);
  }
}
