package com.romrom.application.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.romrom.application.dto.AdminRequest;
import com.romrom.common.constant.ItemStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 관리자 작업 기록 요청 요약 단위 테스트 (#874): 비밀값 마스킹, ID 예외, 잘림, 파일·목록 요약, 이메일 마스킹, 제외 속성
 */
class AdminAuditRequestSummarizerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AdminAuditRequestSummarizer adminAuditRequestSummarizer = new AdminAuditRequestSummarizer(objectMapper);

  private Map<String, Object> summarize(Object... requestObjects) throws Exception {
    Map<String, Object> propertyValueByName = adminAuditRequestSummarizer.readPropertyValues(List.of(requestObjects));
    String requestSummaryJson = adminAuditRequestSummarizer.toRequestSummaryJson(propertyValueByName);
    return objectMapper.readValue(requestSummaryJson, new TypeReference<>() {
    });
  }

  @Test
  @DisplayName("비밀값: password·secret·token 포함 이름은 ***, 원문 미포함")
  void masksSecretProperties() throws Exception {
    AdminRequest adminRequest = AdminRequest.builder()
        .currentPassword("old-password-1234")
        .newPassword("new-password-5678")
        .mailSmtpPassword("smtp-secret")
        .password("plain")
        .build();

    Map<String, Object> requestSummary = summarize(adminRequest);
    String requestSummaryJson = objectMapper.writeValueAsString(requestSummary);

    Assertions.assertEquals("***", requestSummary.get("currentPassword"));
    Assertions.assertEquals("***", requestSummary.get("newPassword"));
    Assertions.assertEquals("***", requestSummary.get("mailSmtpPassword"));
    Assertions.assertEquals("***", requestSummary.get("password"));
    Assertions.assertFalse(requestSummaryJson.contains("old-password-1234"));
    Assertions.assertFalse(requestSummaryJson.contains("new-password-5678"));
    Assertions.assertFalse(requestSummaryJson.contains("smtp-secret"));
  }

  @Test
  @DisplayName("비밀값 이름 판정: token·secret 포함 > 비밀, ID 접미사(fcmTokenId) > 비밀 아님")
  void isSecretPropertyName() {
    Assertions.assertTrue(AdminAuditRequestSummarizer.isSecretPropertyName("fcmToken"));
    Assertions.assertTrue(AdminAuditRequestSummarizer.isSecretPropertyName("refreshToken"));
    Assertions.assertTrue(AdminAuditRequestSummarizer.isSecretPropertyName("clientSecret"));
    Assertions.assertTrue(AdminAuditRequestSummarizer.isSecretPropertyName("PASSWORD"));
    Assertions.assertFalse(AdminAuditRequestSummarizer.isSecretPropertyName("fcmTokenId"));
    Assertions.assertFalse(AdminAuditRequestSummarizer.isSecretPropertyName("itemName"));
  }

  @Test
  @DisplayName("ID 값은 그대로, 페이지 이동값·실행 관리자 제외, null 제외, enum 이름")
  void keepsIdentifiersAndSkipsExcludedProperties() throws Exception {
    UUID fcmTokenId = UUID.randomUUID();
    AdminRequest adminRequest = AdminRequest.builder()
        .fcmTokenId(fcmTokenId)
        .itemStatus(ItemStatus.EXCHANGED)
        .executorAdminId(UUID.randomUUID())
        .pageNumber(3)
        .pageSize(50)
        .build();

    Map<String, Object> requestSummary = summarize(adminRequest);

    Assertions.assertEquals(fcmTokenId.toString(), requestSummary.get("fcmTokenId"));
    Assertions.assertEquals("EXCHANGED", requestSummary.get("itemStatus"));
    Assertions.assertFalse(requestSummary.containsKey("executorAdminId"));
    Assertions.assertFalse(requestSummary.containsKey("pageNumber"));
    Assertions.assertFalse(requestSummary.containsKey("pageSize"));
    Assertions.assertFalse(requestSummary.containsKey("itemName"));
  }

  @Test
  @DisplayName("긴 값 200자 + ... 로 잘림, 이메일 마스킹")
  void truncatesLongValuesAndMasksEmails() throws Exception {
    AdminRequest adminRequest = AdminRequest.builder()
        .itemDescription("가".repeat(500))
        .alertEmail("alertowner@gmail.com")
        .build();

    Map<String, Object> requestSummary = summarize(adminRequest);

    String truncatedDescription = (String) requestSummary.get("itemDescription");
    Assertions.assertEquals(AdminAuditRequestSummarizer.VALUE_MAXIMUM_LENGTH + 3, truncatedDescription.length());
    Assertions.assertTrue(truncatedDescription.endsWith("..."));
    Assertions.assertEquals("al***@gmail.com", requestSummary.get("alertEmail"));
  }

  @Test
  @DisplayName("요약 JSON 전체 길이 4000자 제한")
  void truncatesWholeRequestSummary() {
    List<String> longValues = IntStream.range(0, 20).mapToObj(index -> "x".repeat(300)).collect(Collectors.toList());
    String requestSummaryJson = adminAuditRequestSummarizer.toRequestSummaryJson(Map.of("longValues", longValues));

    Assertions.assertEquals(AdminAuditRequestSummarizer.REQUEST_SUMMARY_MAXIMUM_LENGTH + 3, requestSummaryJson.length());
  }

  @Test
  @DisplayName("파일은 이름·크기만 (내용 미포함)")
  void summarizesMultipartFileWithoutContent() throws Exception {
    byte[] imageBytes = "PNG-CONTENT-SHOULD-NOT-APPEAR".getBytes();
    MockMultipartFile homePopupImage = new MockMultipartFile("homePopupImage", "popup.png", "image/png", imageBytes);
    AdminRequest adminRequest = AdminRequest.builder().homePopupImage(homePopupImage).build();

    Map<String, Object> requestSummary = summarize(adminRequest);

    Assertions.assertEquals("file: popup.png (" + imageBytes.length + " bytes)", requestSummary.get("homePopupImage"));
    Assertions.assertFalse(objectMapper.writeValueAsString(requestSummary).contains("PNG-CONTENT"));
  }

  @Test
  @DisplayName("목록은 앞 20개 + 전체 개수 표시")
  void summarizesLongCollections() throws Exception {
    List<UUID> memberIds = IntStream.range(0, 25).mapToObj(index -> UUID.randomUUID()).collect(Collectors.toList());
    AdminRequest adminRequest = AdminRequest.builder().memberIds(memberIds).build();

    Map<String, Object> requestSummary = summarize(adminRequest);

    List<?> memberIdSummary = (List<?>) requestSummary.get("memberIds");
    Assertions.assertEquals(AdminAuditRequestSummarizer.COLLECTION_PREVIEW_COUNT + 1, memberIdSummary.size());
    Assertions.assertEquals("... total 25", memberIdSummary.get(memberIdSummary.size() - 1));
  }

  @Test
  @DisplayName("요청 객체 여러 개: 앞 객체 값 우선 병합, 값이 하나도 없으면 null")
  void mergesMultipleRequestObjects() throws Exception {
    UUID memberId = UUID.randomUUID();
    AdminRequest adminRequest = AdminRequest.builder().memberId(memberId).adminNotificationTitle("공지").build();

    Map<String, Object> requestSummary = summarize(adminRequest, new Object());

    Assertions.assertEquals(memberId.toString(), requestSummary.get("memberId"));
    Assertions.assertEquals("공지", requestSummary.get("adminNotificationTitle"));
    Assertions.assertNull(adminAuditRequestSummarizer.toRequestSummaryJson(
        adminAuditRequestSummarizer.readPropertyValues(List.of(new Object()))));
  }
}
