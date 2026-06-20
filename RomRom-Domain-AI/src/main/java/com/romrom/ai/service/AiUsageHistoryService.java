package com.romrom.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.ai.repository.mongo.AiUsageHistoryRepository;
import com.romrom.common.constant.AiUsageType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiUsageHistoryService {

  private static final int MAX_PAYLOAD_BYTES = 50 * 1024;
  private static final String MASKED_PLACEHOLDER = "***MASKED***";
  private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
      "password",
      "token",
      "accesstoken",
      "refreshtoken",
      "firebasetoken",
      "authorization",
      "secret"
  );

  private final AiUsageHistoryRepository aiUsageHistoryRepository;
  private final ObjectMapper objectMapper;

  @Async
  public void record(
      UUID memberId,
      AiUsageType aiUsageType,
      UUID relatedEntityId,
      Map<String, Object> requestPayload,
      Map<String, Object> responsePayload,
      Boolean isSuccess,
      String errorMessage,
      Long durationMs,
      String modelName
  ) {
    try {
      Map<String, Object> maskedRequestPayload = sanitizePayload(requestPayload);
      Map<String, Object> maskedResponsePayload = sanitizePayload(responsePayload);

      Map<String, Object> truncatedRequestPayload = truncateIfExceedsLimit(maskedRequestPayload);
      Map<String, Object> truncatedResponsePayload = truncateIfExceedsLimit(maskedResponsePayload);

      AiUsageHistory aiUsageHistoryEntity = AiUsageHistory.builder()
          .memberId(memberId)
          .aiUsageType(aiUsageType)
          .relatedEntityId(relatedEntityId)
          .requestedAt(LocalDateTime.now())
          .requestPayload(truncatedRequestPayload)
          .responsePayload(truncatedResponsePayload)
          .isSuccess(isSuccess)
          .errorMessage(errorMessage)
          .durationMs(durationMs)
          .modelName(modelName)
          .build();

      aiUsageHistoryRepository.save(aiUsageHistoryEntity);
    } catch (Exception saveException) {
      log.warn("AiUsageHistory 저장 실패: aiUsageType={}, memberId={}, error={}",
          aiUsageType, memberId, saveException.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sanitizePayload(Map<String, Object> originalPayload) {
    if (originalPayload == null) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> sanitizedPayload = new LinkedHashMap<>();
    for (Map.Entry<String, Object> payloadEntry : originalPayload.entrySet()) {
      String payloadFieldName = payloadEntry.getKey();
      Object payloadFieldValue = payloadEntry.getValue();

      if (isSensitiveKeyName(payloadFieldName)) {
        sanitizedPayload.put(payloadFieldName, MASKED_PLACEHOLDER);
        continue;
      }

      sanitizedPayload.put(payloadFieldName, sanitizeNestedValue(payloadFieldValue));
    }
    return sanitizedPayload;
  }

  @SuppressWarnings("unchecked")
  private Object sanitizeNestedValue(Object nestedValue) {
    if (nestedValue instanceof Map<?, ?> nestedMapValue) {
      Map<String, Object> castedNestedMap = new LinkedHashMap<>();
      for (Map.Entry<?, ?> nestedEntry : nestedMapValue.entrySet()) {
        castedNestedMap.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
      }
      return sanitizePayload(castedNestedMap);
    }
    if (nestedValue instanceof List<?> nestedListValue) {
      List<Object> sanitizedList = new ArrayList<>(nestedListValue.size());
      for (Object listElement : nestedListValue) {
        sanitizedList.add(sanitizeNestedValue(listElement));
      }
      return sanitizedList;
    }
    return nestedValue;
  }

  private boolean isSensitiveKeyName(String fieldName) {
    if (fieldName == null) {
      return false;
    }
    return SENSITIVE_KEY_NAMES.contains(fieldName.toLowerCase().replace("_", "").replace("-", ""));
  }

  private Map<String, Object> truncateIfExceedsLimit(Map<String, Object> sanitizedPayload) {
    try {
      String serializedJson = objectMapper.writeValueAsString(sanitizedPayload);
      if (serializedJson.length() <= MAX_PAYLOAD_BYTES) {
        return sanitizedPayload;
      }
      Map<String, Object> truncatedPayload = new HashMap<>();
      String truncatedJsonSnippet = serializedJson.substring(0, MAX_PAYLOAD_BYTES);
      truncatedPayload.put("truncated", true);
      truncatedPayload.put("originalLength", serializedJson.length());
      truncatedPayload.put("snippet", truncatedJsonSnippet);
      return truncatedPayload;
    } catch (JsonProcessingException serializeException) {
      Map<String, Object> fallbackPayload = new HashMap<>();
      fallbackPayload.put("truncated", true);
      fallbackPayload.put("serializeError", serializeException.getMessage());
      return fallbackPayload;
    }
  }
}
