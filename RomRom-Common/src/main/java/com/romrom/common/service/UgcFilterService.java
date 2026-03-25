package com.romrom.common.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private static final String UGC_FILTER_PATTERNS_CONFIG_KEY = "ugc.filter.patterns";
  // ObjectMapper를 Spring 빈으로 주입하지 않고 static 필드로 사용
  // 이유: RomRom-Common 모듈에 ObjectMapper @Bean이 없음 (JacksonConfig는 RomRom-Web에 있음)
  private static final ObjectMapper UGC_OBJECT_MAPPER = new ObjectMapper();

  private final SystemConfigCacheService systemConfigCacheService;

  private volatile List<Pattern> compiledUgcPatternCache = null;
  private volatile String lastLoadedPatternJson = null;

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

  public void invalidateCompiledPatternCache() {
    this.compiledUgcPatternCache = null;
    this.lastLoadedPatternJson = null;
    log.info("UGC 필터 인메모리 패턴 캐시 초기화 완료");
  }

  private List<Pattern> getCompiledUgcPatterns() {
    String rawUgcPatternJson = systemConfigCacheService.get(UGC_FILTER_PATTERNS_CONFIG_KEY);

    if (rawUgcPatternJson == null) {
      return List.of();
    }

    if (compiledUgcPatternCache != null && rawUgcPatternJson.equals(lastLoadedPatternJson)) {
      return compiledUgcPatternCache;
    }

    List<String> ugcPatternStrings;
    try {
      ugcPatternStrings = UGC_OBJECT_MAPPER.readValue(rawUgcPatternJson, new TypeReference<>() {});
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

    // compiledUgcPatternCache 먼저 세팅: lastLoadedPatternJson이 먼저 세팅되면
    // 다른 스레드가 캐시 히트로 판단 후 아직 null인 캐시를 반환할 수 있음
    this.compiledUgcPatternCache = newCompiledUgcPatterns;
    this.lastLoadedPatternJson = rawUgcPatternJson;
    return newCompiledUgcPatterns;
  }
}
