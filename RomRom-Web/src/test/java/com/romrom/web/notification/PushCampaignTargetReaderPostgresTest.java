package com.romrom.web.notification;

import com.romrom.common.constant.DeviceType;
import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.notification.campaign.PushCampaignTargetFilter;
import com.romrom.notification.campaign.PushCampaignTargetQuery;
import com.romrom.notification.campaign.PushCampaignTargetQueryBuilder;
import com.romrom.notification.campaign.PushCampaignTargetReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 캠페인 대상 SQL 실DB 실행 확인 (#874): 대상 4종 + SEGMENT 조건 전부가 PostgreSQL 에서 문법 오류 없이 실행되는지
 * 수동 실행 전용: -Dromrom.test.postgres.url=jdbc:postgresql://... (V1_4_76·77 적용된 임시 DB)
 */
@Tag("integration")
@EnabledIf("isPostgresConfigured")
class PushCampaignTargetReaderPostgresTest {

  private static boolean isPostgresConfigured() {
    return System.getProperty("romrom.test.postgres.url") != null;
  }

  private static PushCampaignTargetReader createReader() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(System.getProperty("romrom.test.postgres.url"),
        System.getProperty("romrom.test.postgres.username", "postgres"),
        System.getProperty("romrom.test.postgres.password", ""));
    return new PushCampaignTargetReader(new NamedParameterJdbcTemplate(dataSource));
  }

  @Test
  @DisplayName("대상 4종·SEGMENT 전 조건 SQL 이 실행되고 페이지·토큰 조회가 동작")
  void targetQueries_실DB_실행() {
    PushCampaignTargetReader targetReader = createReader();
    LocalDateTime now = LocalDateTime.now();
    PushCampaignTargetFilter segmentFilter = PushCampaignTargetFilter.builder()
        .deviceType(DeviceType.IOS).isMarketingAgreed(true).activeWithinDays(30).inactiveForDays(1)
        .joinedFromDate(LocalDate.now().minusYears(5)).joinedToDate(LocalDate.now()).siDo("서울_%")
        .build();
    PushCampaignTargetFilter membersFilter = PushCampaignTargetFilter.builder()
        .memberIdentifiers(List.of(UUID.randomUUID().toString(), "someone@example.com", "nick")).build();
    PushCampaignTargetFilter testFilter = PushCampaignTargetFilter.builder().testMemberId(UUID.randomUUID()).build();

    for (PushCampaignPurpose purpose : PushCampaignPurpose.values()) {
      for (PushCampaignTargetType targetType : PushCampaignTargetType.values()) {
        PushCampaignTargetFilter targetFilter = switch (targetType) {
          case SEGMENT -> segmentFilter;
          case MEMBERS -> membersFilter;
          case TEST -> testFilter;
          case ALL -> PushCampaignTargetFilter.empty();
        };
        PushCampaignTargetQuery targetQuery = PushCampaignTargetQueryBuilder.build(purpose, targetType, targetFilter, now);
        long targetMemberCount = targetReader.countTargetMembers(targetQuery);
        Assertions.assertTrue(targetReader.countPushConsentedMembers(targetQuery) <= targetMemberCount);
        Assertions.assertTrue(targetReader.countPushConsentedTokens(targetQuery) >= 0);
        Assertions.assertTrue(targetReader.readTargetPage(targetQuery, null, 10).size() <= 10);
        Assertions.assertTrue(targetReader.readTargetPage(targetQuery, UUID.randomUUID(), 10).size() <= 10);
      }
    }
    Assertions.assertTrue(targetReader.readPushTargets(List.of(UUID.randomUUID()), DeviceType.ANDROID).isEmpty());
  }
}
