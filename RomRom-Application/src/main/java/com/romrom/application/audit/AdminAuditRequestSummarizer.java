package com.romrom.application.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.util.LogMaskUtil;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 작업 기록 요청 요약: 요청 객체 getter 값 읽기 > 비밀값 ***, 긴 값 잘림, 파일은 이름·크기, 이메일 마스킹 > JSON
 * 클래스별 읽기 속성 목록은 1회 계산 후 캐시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAuditRequestSummarizer {

  // 값 1개 최대 길이 (초과분 잘림)
  static final int VALUE_MAXIMUM_LENGTH = 200;
  // 목록 값에서 남길 앞쪽 개수
  static final int COLLECTION_PREVIEW_COUNT = 20;
  // 요청 요약 JSON 최대 길이
  static final int REQUEST_SUMMARY_MAXIMUM_LENGTH = 4000;
  static final String SECRET_MASK = "***";
  static final String TRUNCATED_SUFFIX = "...";

  // 이름에 포함되면 비밀값 (대소문자 무시): password, mailSmtpPassword, fcmToken, clientSecret 등
  private static final List<String> SECRET_NAME_KEYWORDS = List.of("password", "secret", "token");
  // 식별자 접미사 (fcmTokenId 등은 비밀값 아님)
  private static final List<String> IDENTIFIER_NAME_SUFFIXES = List.of("Id", "Ids");
  // 기록 제외: 페이지 이동값, 실행 관리자(별도 컬럼)
  private static final Set<String> EXCLUDED_PROPERTY_NAMES = Set.of("class", "pageNumber", "pageSize", "executorAdminId");

  private final ObjectMapper objectMapper;
  private final Map<Class<?>, List<PropertyDescriptor>> readablePropertiesByClass = new ConcurrentHashMap<>();

  /**
   * 요청 객체들 > 속성 이름별 원래 값 (null 제외, 앞 객체 우선)
   */
  public Map<String, Object> readPropertyValues(List<Object> requestObjects) {
    Map<String, Object> propertyValueByName = new LinkedHashMap<>();
    for (Object requestObject : requestObjects) {
      if (requestObject == null) {
        continue;
      }
      for (PropertyDescriptor readableProperty : findReadableProperties(requestObject.getClass())) {
        Object propertyValue = invokeGetter(readableProperty.getReadMethod(), requestObject);
        if (propertyValue != null) {
          propertyValueByName.putIfAbsent(readableProperty.getName(), propertyValue);
        }
      }
    }
    return propertyValueByName;
  }

  /**
   * 속성 값 > 요청 요약 JSON (비밀값 ***, 긴 값 잘림, 파일 이름·크기만, 전체 길이 제한)
   */
  public String toRequestSummaryJson(Map<String, Object> propertyValueByName) {
    if (propertyValueByName.isEmpty()) {
      return null;
    }
    Map<String, Object> summaryValueByName = new LinkedHashMap<>();
    propertyValueByName.forEach((propertyName, propertyValue) -> summaryValueByName.put(propertyName,
        isSecretPropertyName(propertyName) ? SECRET_MASK : toSummaryValue(propertyValue)));
    try {
      return truncate(objectMapper.writeValueAsString(summaryValueByName), REQUEST_SUMMARY_MAXIMUM_LENGTH);
    } catch (JsonProcessingException jsonProcessingException) {
      log.warn("[admin audit] request summary serialization failed : {}", jsonProcessingException.getClass().getSimpleName());
      return null;
    }
  }

  /**
   * 비밀값 이름 여부: password·secret·token 포함, 단 ID 접미사(fcmTokenId)는 제외
   */
  static boolean isSecretPropertyName(String propertyName) {
    if (propertyName == null) {
      return false;
    }
    boolean isIdentifierName = IDENTIFIER_NAME_SUFFIXES.stream().anyMatch(propertyName::endsWith);
    if (isIdentifierName) {
      return false;
    }
    String lowerCasePropertyName = propertyName.toLowerCase(Locale.ROOT);
    return SECRET_NAME_KEYWORDS.stream().anyMatch(lowerCasePropertyName::contains);
  }

  /**
   * 값 1개 > 요약 값 (숫자·불리언 그대로, 파일은 이름·크기, 목록은 앞 20개, 문자열은 이메일 마스킹 + 200자)
   */
  static Object toSummaryValue(Object propertyValue) {
    if (propertyValue instanceof Number || propertyValue instanceof Boolean) {
      return propertyValue;
    }
    if (propertyValue instanceof MultipartFile multipartFile) {
      return toFileSummary(multipartFile);
    }
    if (propertyValue instanceof Collection<?> collectionValue) {
      return toCollectionSummary(collectionValue);
    }
    if (propertyValue instanceof Object[] arrayValue) {
      return toCollectionSummary(Arrays.asList(arrayValue));
    }
    if (propertyValue instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    return truncate(LogMaskUtil.maskEmailsInText(String.valueOf(propertyValue)), VALUE_MAXIMUM_LENGTH);
  }

  static String truncate(String text, int maximumLength) {
    if (text == null || text.length() <= maximumLength) {
      return text;
    }
    return text.substring(0, maximumLength) + TRUNCATED_SUFFIX;
  }

  // 파일 내용은 읽지 않음 (이름·크기만)
  private static String toFileSummary(MultipartFile multipartFile) {
    String originalFileName = multipartFile.getOriginalFilename();
    return "file: " + truncate(originalFileName == null ? "-" : originalFileName, VALUE_MAXIMUM_LENGTH)
        + " (" + multipartFile.getSize() + " bytes)";
  }

  private static List<Object> toCollectionSummary(Collection<?> collectionValue) {
    List<Object> previewValues = new ArrayList<>();
    int elementIndex = 0;
    for (Object elementValue : collectionValue) {
      if (elementIndex++ >= COLLECTION_PREVIEW_COUNT) {
        previewValues.add("... total " + collectionValue.size());
        break;
      }
      previewValues.add(elementValue == null ? null : toSummaryValue(elementValue));
    }
    return previewValues;
  }

  private List<PropertyDescriptor> findReadableProperties(Class<?> requestClass) {
    return readablePropertiesByClass.computeIfAbsent(requestClass, targetClass ->
        Arrays.stream(BeanUtils.getPropertyDescriptors(targetClass))
            .filter(propertyDescriptor -> propertyDescriptor.getReadMethod() != null)
            .filter(propertyDescriptor -> !EXCLUDED_PROPERTY_NAMES.contains(propertyDescriptor.getName()))
            .toList());
  }

  private static Object invokeGetter(Method readMethod, Object requestObject) {
    try {
      return readMethod.invoke(requestObject);
    } catch (ReflectiveOperationException | RuntimeException getterException) {
      return null;
    }
  }
}
