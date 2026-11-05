package com.romrom.notification.campaign;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 캠페인 대상 SQL 조건 단위 테스트 (#874): 대상 4종, 광고성 동의 조건, SEGMENT 6개 바인딩, 식별자 분리, LIKE 이스케이프
 */
class PushCampaignTargetQueryBuilderTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

  @Test
  @DisplayName("ALL·정보성: 활성 회원 조건, 푸시는 콘텐츠 알림 동의자, 마케팅 조건 없음")
  void all_정보성() {
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.ALL, PushCampaignTargetFilter.empty(), NOW);

    Assertions.assertTrue(targetQuery.targetWhereClause().contains("m.is_deleted = false"));
    Assertions.assertTrue(targetQuery.targetWhereClause().contains("'SUSPENDED_ACCOUNT'"));
    Assertions.assertFalse(targetQuery.targetWhereClause().contains("is_marketing_info_agreed"));
    Assertions.assertEquals("m.is_content_notification_agreed = true", targetQuery.pushConsentClause());
    Assertions.assertTrue(targetQuery.parameters().isEmpty());
    Assertions.assertNull(targetQuery.tokenDeviceType());
  }

  @Test
  @DisplayName("ALL·광고성: 마케팅 동의자만 대상 + 푸시 동의 조건도 마케팅")
  void all_광고성() {
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.ADVERTISEMENT,
        PushCampaignTargetType.ALL, null, NOW);

    Assertions.assertTrue(targetQuery.targetWhereClause().endsWith("AND m.is_marketing_info_agreed = true"));
    Assertions.assertEquals("m.is_marketing_info_agreed = true", targetQuery.pushConsentClause());
  }

  @Test
  @DisplayName("SEGMENT: 조건 6개 모두 바인딩 파라미터로, 기기 조건은 토큰 기기 필터에도 반영")
  void segment_전조건() {
    PushCampaignTargetFilter segmentFilter = PushCampaignTargetFilter.builder()
        .deviceType(DeviceType.IOS).isMarketingAgreed(false).activeWithinDays(7).inactiveForDays(30)
        .joinedFromDate(LocalDate.of(2026, 1, 1)).joinedToDate(LocalDate.of(2026, 1, 31)).siDo("서울")
        .build();

    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.SEGMENT, segmentFilter, NOW);

    String whereClause = targetQuery.targetWhereClause();
    Assertions.assertTrue(whereClause.contains("f_device.device_type = :deviceTypeOrdinal"));
    Assertions.assertTrue(whereClause.contains("m.is_marketing_info_agreed = :isMarketingAgreed"));
    Assertions.assertTrue(whereClause.contains("m.last_active_at >= :activeSinceAt"));
    Assertions.assertTrue(whereClause.contains("m.last_active_at < :inactiveBeforeAt"));
    Assertions.assertTrue(whereClause.contains("m.created_date >= :joinedFromAt"));
    Assertions.assertTrue(whereClause.contains("m.created_date < :joinedToExclusiveAt"));
    Assertions.assertTrue(whereClause.contains("ml.si_do LIKE :siDoPrefix0"));
    Assertions.assertFalse(whereClause.contains("서울"), "값은 SQL 문자열에 들어가지 않음");

    Assertions.assertEquals(DeviceType.IOS.ordinal(), targetQuery.parameters().get("deviceTypeOrdinal"));
    Assertions.assertEquals(false, targetQuery.parameters().get("isMarketingAgreed"));
    Assertions.assertEquals(NOW.minusDays(7), targetQuery.parameters().get("activeSinceAt"));
    Assertions.assertEquals(NOW.minusDays(30), targetQuery.parameters().get("inactiveBeforeAt"));
    Assertions.assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), targetQuery.parameters().get("joinedFromAt"));
    Assertions.assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0), targetQuery.parameters().get("joinedToExclusiveAt"));
    Assertions.assertEquals("서울%", targetQuery.parameters().get("siDoPrefix0"));
    Assertions.assertEquals(DeviceType.IOS, targetQuery.tokenDeviceType());
  }

  @Test
  @DisplayName("SEGMENT 빈 조건 > 활성 회원 조건만")
  void segment_빈조건() {
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.SEGMENT, PushCampaignTargetFilter.empty(), NOW);
    Assertions.assertTrue(targetQuery.parameters().isEmpty());
    Assertions.assertFalse(targetQuery.targetWhereClause().contains("EXISTS"));
  }

  @Test
  @DisplayName("MEMBERS: UUID 는 회원 ID, 나머지는 이메일·닉네임, 탈퇴만 제외(정지 회원 포함)")
  void members_식별자분리() {
    UUID memberId = UUID.randomUUID();
    PushCampaignTargetFilter membersFilter = PushCampaignTargetFilter.builder()
        .memberIdentifiers(List.of(memberId.toString(), "user@example.com", "닉네임", " ", "1-2-3-4-5")).build();

    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.MEMBERS, membersFilter, NOW);

    Assertions.assertEquals(List.of(memberId), targetQuery.parameters().get("identifierMemberIds"));
    Assertions.assertEquals(List.of("user@example.com", "닉네임", "1-2-3-4-5"), targetQuery.parameters().get("identifierTexts"));
    Assertions.assertTrue(targetQuery.targetWhereClause().contains("m.email IN (:identifierTexts)"));
    Assertions.assertFalse(targetQuery.targetWhereClause().contains("SUSPENDED_ACCOUNT"));
  }

  @Test
  @DisplayName("MEMBERS 식별자 없음·TEST 관리자 ID 없음 > 대상 없음(FALSE)")
  void 대상없음_FALSE() {
    PushCampaignTargetQuery membersQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.MEMBERS, PushCampaignTargetFilter.empty(), NOW);
    PushCampaignTargetQuery testQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.TEST, PushCampaignTargetFilter.empty(), NOW);
    Assertions.assertTrue(membersQuery.targetWhereClause().contains("FALSE"));
    Assertions.assertEquals("FALSE", testQuery.targetWhereClause());
  }

  @Test
  @DisplayName("TEST·광고성: 관리자 본인만, 동의 조건 없음")
  void test_관리자본인() {
    UUID adminId = UUID.randomUUID();
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.ADVERTISEMENT,
        PushCampaignTargetType.TEST, PushCampaignTargetFilter.builder().testMemberId(adminId).build(), NOW);

    Assertions.assertEquals("m.member_id = :testMemberId", targetQuery.targetWhereClause());
    Assertions.assertEquals("TRUE", targetQuery.pushConsentClause());
    Assertions.assertEquals(adminId, targetQuery.parameters().get("testMemberId"));
  }

  @Test
  @DisplayName("시·도 옛·새 이름 '|' 구분 > OR 조건")
  void segment_시도대체이름() {
    PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(PushCampaignPurpose.INFORMATION,
        PushCampaignTargetType.SEGMENT, PushCampaignTargetFilter.builder().siDo("전북|전라북도").build(), NOW);

    Assertions.assertTrue(targetQuery.targetWhereClause().contains(
        "ml.si_do LIKE :siDoPrefix0 ESCAPE '\\' OR ml.si_do LIKE :siDoPrefix1 ESCAPE '\\'"));
    Assertions.assertEquals("전북%", targetQuery.parameters().get("siDoPrefix0"));
    Assertions.assertEquals("전라북도%", targetQuery.parameters().get("siDoPrefix1"));
  }

  @Test
  @DisplayName("시·도 LIKE 특수문자 이스케이프")
  void likeEscape() {
    Assertions.assertEquals("a\\%b\\_c\\\\", PushCampaignTargetQueryBuilder.escapeLikePattern("a%b_c\\"));
  }

  @Test
  @DisplayName("대상 조건 JSON 저장·복원")
  void targetFilterJson_왕복() {
    PushCampaignTargetFilter originalFilter = PushCampaignTargetFilter.builder()
        .deviceType(DeviceType.ANDROID).isMarketingAgreed(true).activeWithinDays(3).inactiveForDays(60)
        .joinedFromDate(LocalDate.of(2026, 1, 1)).joinedToDate(LocalDate.of(2026, 3, 1)).siDo("경기")
        .memberIdentifiers(List.of("a@b.com", "nick")).testMemberId(UUID.randomUUID())
        .build();

    Assertions.assertEquals(originalFilter, PushCampaignTargetFilter.fromJson(originalFilter.toJson()));
    Assertions.assertEquals(PushCampaignTargetFilter.empty().toBuilder().memberIdentifiers(List.of()).build(),
        PushCampaignTargetFilter.fromJson(null));
  }
}
