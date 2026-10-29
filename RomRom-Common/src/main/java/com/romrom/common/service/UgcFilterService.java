package com.romrom.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.common.constant.SystemConfigKeys;
import com.romrom.common.exception.UgcProhibitedContentException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class UgcFilterService {

  // ObjectMapper를 Spring 빈으로 주입하지 않고 static 필드로 사용
  // 이유: RomRom-Common 모듈에 ObjectMapper @Bean이 없음 (JacksonConfig는 RomRom-Web에 있음)
  private static final ObjectMapper UGC_OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST_TYPE_REFERENCE = new TypeReference<>() {};

  private final SystemConfigCacheService systemConfigCacheService;

  // 두 필드를 불변 record로 묶어 단일 volatile 참조로 관리 → 원자적 캐시 갱신 보장
  private record CompiledUgcPatternSnapshot(String sourcePatternJson, List<Pattern> compiledPatterns) {}

  private volatile CompiledUgcPatternSnapshot ugcPatternSnapshot = null;

  public void validate(String ugcText, String fieldName) {
    if (!StringUtils.hasText(ugcText)) {
      return;
    }

    List<Pattern> compiledPatterns = getCompiledUgcPatterns();
    if (compiledPatterns.isEmpty()) {
      return;
    }

    for (Pattern compiledUgcPattern : compiledPatterns) {
      java.util.regex.Matcher ugcMatcher = compiledUgcPattern.matcher(ugcText);
      if (ugcMatcher.find()) {
        String violatingText = ugcMatcher.group();
        log.warn("UGC 필터링 위반 감지: field={}, violatingText={}", fieldName, violatingText);
        throw new UgcProhibitedContentException(violatingText, fieldName);
      }
    }
  }

  /**
   * 비속어 포함 여부만 감지 (예외를 던지지 않음, 채팅 경고용)
   */
  public boolean containsProhibitedContent(String ugcText) {
    if (!StringUtils.hasText(ugcText)) {
      return false;
    }

    List<Pattern> compiledPatterns = getCompiledUgcPatterns();
    for (Pattern compiledUgcPattern : compiledPatterns) {
      if (compiledUgcPattern.matcher(ugcText).find()) {
        return true;
      }
    }
    return false;
  }

  public void invalidateCompiledPatternCache() {
    this.ugcPatternSnapshot = null;
    log.info("UGC 필터 인메모리 패턴 캐시 초기화 완료");
  }

  private List<Pattern> getCompiledUgcPatterns() {
    String rawUgcPatternJson = systemConfigCacheService.get(SystemConfigKeys.UGC_FILTER_PATTERNS);

    if (rawUgcPatternJson == null) {
      return List.of();
    }

    CompiledUgcPatternSnapshot currentSnapshot = this.ugcPatternSnapshot;
    if (currentSnapshot != null && rawUgcPatternJson.equals(currentSnapshot.sourcePatternJson())) {
      return currentSnapshot.compiledPatterns();
    }

    List<String> ugcPatternStrings;
    try {
      ugcPatternStrings = UGC_OBJECT_MAPPER.readValue(rawUgcPatternJson, STRING_LIST_TYPE_REFERENCE);
    } catch (Exception e) {
      log.warn("UGC 필터 패턴 JSON 파싱 실패: {}", e.getMessage());
      return List.of();
    }

    List<Pattern> newCompiledUgcPatterns = new ArrayList<>();
    for (String ugcPatternString : ugcPatternStrings) {
      try {
        newCompiledUgcPatterns.add(Pattern.compile(ugcPatternString, Pattern.CASE_INSENSITIVE));
      } catch (PatternSyntaxException e) {
        log.warn("UGC 필터 패턴 컴파일 실패 (건너뜀): pattern={}, error={}", ugcPatternString, e.getMessage());
      }
    }

    this.ugcPatternSnapshot = new CompiledUgcPatternSnapshot(rawUgcPatternJson, newCompiledUgcPatterns);
    return newCompiledUgcPatterns;
  }
}
