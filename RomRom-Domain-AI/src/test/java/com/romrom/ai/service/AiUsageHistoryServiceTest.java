package com.romrom.ai.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.ai.entity.mongo.AiUsageHistory;
import com.romrom.ai.repository.mongo.AiUsageHistoryRepository;
import com.romrom.common.constant.AiUsageType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AiUsageHistoryService 단위 테스트 (Issue #708)
 *
 * 검증 대상:
 *  - 민감 키 마스킹 (password, token, accessToken, refreshToken, firebaseToken)
 *  - 중첩 Map 내부 민감 키도 마스킹
 *  - 비민감 키는 원본 값 보존
 *  - repository.save 실패 시 예외 전파 없이 정상 종료
 *
 * 마스킹 placeholder = "***MASKED***" (AiUsageHistoryService 상수와 동일)
 */
@ExtendWith(MockitoExtension.class)
class AiUsageHistoryServiceTest {

  private static final String MASKED_PLACEHOLDER = "***MASKED***";

  @Mock
  private AiUsageHistoryRepository aiUsageHistoryRepository;

  private ObjectMapper objectMapper;

  private AiUsageHistoryService aiUsageHistoryService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    aiUsageHistoryService = new AiUsageHistoryService(aiUsageHistoryRepository, objectMapper);
  }

  @Test
  @DisplayName("record_민감키_password_마스킹")
  void record_민감키_password_마스킹() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    Map<String, Object> requestPayloadWithPassword = new LinkedHashMap<>();
    requestPayloadWithPassword.put("itemName", "iPhone 15");
    requestPayloadWithPassword.put("password", "super-secret");

    // when
    aiUsageHistoryService.record(
        targetMemberId,
        AiUsageType.UGC_FILTER,
        null,
        requestPayloadWithPassword,
        null,
        true,
        null,
        100L,
        "gemini-1.5"
    );

    // then
    ArgumentCaptor<AiUsageHistory> aiUsageHistoryCaptor = ArgumentCaptor.forClass(AiUsageHistory.class);
    verify(aiUsageHistoryRepository, times(1)).save(aiUsageHistoryCaptor.capture());

    Map<String, Object> savedRequestPayload = aiUsageHistoryCaptor.getValue().getRequestPayload();
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("password"));
    Assertions.assertEquals("iPhone 15", savedRequestPayload.get("itemName"));
  }

  @Test
  @DisplayName("record_민감키_token_accessToken_refreshToken_firebaseToken_마스킹")
  void record_민감키_token_accessToken_refreshToken_firebaseToken_마스킹() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    Map<String, Object> requestPayloadWithTokens = new LinkedHashMap<>();
    requestPayloadWithTokens.put("token", "tk1");
    requestPayloadWithTokens.put("accessToken", "at1");
    requestPayloadWithTokens.put("refreshToken", "rt1");
    requestPayloadWithTokens.put("firebaseToken", "fb1");
    requestPayloadWithTokens.put("authorization", "Bearer xyz");
    requestPayloadWithTokens.put("secret", "shh");

    // when
    aiUsageHistoryService.record(
        targetMemberId,
        AiUsageType.PRICE_PREDICTION,
        null,
        requestPayloadWithTokens,
        null,
        true,
        null,
        50L,
        "gemini-1.5"
    );

    // then
    ArgumentCaptor<AiUsageHistory> aiUsageHistoryCaptor = ArgumentCaptor.forClass(AiUsageHistory.class);
    verify(aiUsageHistoryRepository, times(1)).save(aiUsageHistoryCaptor.capture());

    Map<String, Object> savedRequestPayload = aiUsageHistoryCaptor.getValue().getRequestPayload();
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("token"));
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("accessToken"));
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("refreshToken"));
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("firebaseToken"));
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("authorization"));
    Assertions.assertEquals(MASKED_PLACEHOLDER, savedRequestPayload.get("secret"));
  }

  @Test
  @DisplayName("record_민감키_중첩Map_마스킹")
  @SuppressWarnings("unchecked")
  void record_민감키_중첩Map_마스킹() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    Map<String, Object> nestedSensitiveMap = new LinkedHashMap<>();
    nestedSensitiveMap.put("password", "xxx");
    nestedSensitiveMap.put("normalField", "keep-me");

    Map<String, Object> rootRequestPayload = new LinkedHashMap<>();
    rootRequestPayload.put("a", nestedSensitiveMap);

    // when
    aiUsageHistoryService.record(
        targetMemberId,
        AiUsageType.IMAGE_ANALYSIS,
        null,
        rootRequestPayload,
        null,
        true,
        null,
        80L,
        "gemini-1.5"
    );

    // then
    ArgumentCaptor<AiUsageHistory> aiUsageHistoryCaptor = ArgumentCaptor.forClass(AiUsageHistory.class);
    verify(aiUsageHistoryRepository, times(1)).save(aiUsageHistoryCaptor.capture());

    Map<String, Object> savedRequestPayload = aiUsageHistoryCaptor.getValue().getRequestPayload();
    Object nestedSanitized = savedRequestPayload.get("a");
    Assertions.assertTrue(nestedSanitized instanceof Map, "중첩 값은 Map이어야 한다");

    Map<String, Object> sanitizedNestedMap = (Map<String, Object>) nestedSanitized;
    Assertions.assertEquals(MASKED_PLACEHOLDER, sanitizedNestedMap.get("password"),
        "중첩 Map 안의 password도 마스킹되어야 한다");
    Assertions.assertEquals("keep-me", sanitizedNestedMap.get("normalField"));
  }

  @Test
  @DisplayName("record_중복없는_정상키_원본보존")
  void record_중복없는_정상키_원본보존() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    Map<String, Object> normalRequestPayload = new LinkedHashMap<>();
    normalRequestPayload.put("itemName", "노트북");
    normalRequestPayload.put("price", 1500000);
    normalRequestPayload.put("category", "ELECTRONICS");

    // when
    aiUsageHistoryService.record(
        targetMemberId,
        AiUsageType.CATEGORY_MATCHING,
        null,
        normalRequestPayload,
        null,
        true,
        null,
        30L,
        "gemini-1.5"
    );

    // then
    ArgumentCaptor<AiUsageHistory> aiUsageHistoryCaptor = ArgumentCaptor.forClass(AiUsageHistory.class);
    verify(aiUsageHistoryRepository, times(1)).save(aiUsageHistoryCaptor.capture());

    Map<String, Object> savedRequestPayload = aiUsageHistoryCaptor.getValue().getRequestPayload();
    Assertions.assertEquals("노트북", savedRequestPayload.get("itemName"));
    Assertions.assertEquals(1500000, savedRequestPayload.get("price"));
    Assertions.assertEquals("ELECTRONICS", savedRequestPayload.get("category"));
  }

  @Test
  @DisplayName("record_저장_실패시_예외_throw하지않음")
  void record_저장_실패시_예외_throw하지않음() {
    // given
    UUID targetMemberId = UUID.randomUUID();
    Map<String, Object> simpleRequestPayload = new HashMap<>();
    simpleRequestPayload.put("k", "v");

    when(aiUsageHistoryRepository.save(any(AiUsageHistory.class)))
        .thenThrow(new RuntimeException("Mongo down"));

    // when & then - 예외 전파 없어야 한다
    Assertions.assertDoesNotThrow(() -> aiUsageHistoryService.record(
        targetMemberId,
        AiUsageType.EMBEDDING,
        null,
        simpleRequestPayload,
        null,
        true,
        null,
        10L,
        "gemini-1.5"
    ));

    verify(aiUsageHistoryRepository, times(1)).save(any(AiUsageHistory.class));
  }
}
