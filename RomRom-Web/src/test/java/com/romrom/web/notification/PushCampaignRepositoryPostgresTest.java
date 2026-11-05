package com.romrom.web.notification;

import com.romrom.common.constant.PushCampaignPurpose;
import com.romrom.common.constant.PushCampaignStatus;
import com.romrom.common.constant.PushCampaignTargetType;
import com.romrom.notification.entity.PushCampaign;
import com.romrom.notification.repository.FcmTokenRepository;
import com.romrom.notification.repository.PushCampaignOpenRepository;
import com.romrom.notification.repository.PushCampaignRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 푸시 캠페인·토큰 저장소 쿼리 실DB 확인 (#874): JPQL 파싱, 조건부 선점·취소·종료, 원자적 집계, 열람 ON CONFLICT
 * 수동 실행 전용: -Dromrom.test.postgres.url=jdbc:postgresql://... (V1_4_76·77 적용된 임시 DB)
 */
@Tag("integration")
@EnabledIf("isPostgresConfigured")
// 로그 파일: 운영 경로(/mnt) 대신 빌드 폴더
@DataJpaTest(properties = "logging.file.name=build/push-campaign-repository-test.log")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PushCampaignRepositoryPostgresTest {

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackages = "com.romrom")
  @EnableJpaRepositories(basePackageClasses = PushCampaignRepository.class)
  @EnableJpaAuditing
  static class PushCampaignRepositoryTestConfig {
  }

  private static boolean isPostgresConfigured() {
    return System.getProperty("romrom.test.postgres.url") != null;
  }

  @DynamicPropertySource
  static void registerDatasource(DynamicPropertyRegistry propertyRegistry) {
    propertyRegistry.add("spring.datasource.url", () -> System.getProperty("romrom.test.postgres.url"));
    propertyRegistry.add("spring.datasource.username", () -> System.getProperty("romrom.test.postgres.username", "postgres"));
    propertyRegistry.add("spring.datasource.password", () -> System.getProperty("romrom.test.postgres.password", ""));
    propertyRegistry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    propertyRegistry.add("spring.flyway.enabled", () -> "false");
  }

  @Autowired
  private PushCampaignRepository pushCampaignRepository;
  @Autowired
  private PushCampaignOpenRepository pushCampaignOpenRepository;
  @Autowired
  private FcmTokenRepository fcmTokenRepository;

  private PushCampaign saveScheduledCampaign(LocalDateTime scheduledAt) {
    return pushCampaignRepository.save(PushCampaign.builder().title("t").body("b")
        .purpose(PushCampaignPurpose.INFORMATION).targetType(PushCampaignTargetType.ALL).targetFilter("{}")
        .status(PushCampaignStatus.SCHEDULED).scheduledAt(scheduledAt).build());
  }

  @Test
  @DisplayName("선점 1회만 성공, 종료·집계·열람 원자적 갱신, 취소는 SCHEDULED 만")
  void campaignQueries_실DB() {
    LocalDateTime now = LocalDateTime.now();
    PushCampaign dueCampaign = saveScheduledCampaign(now.minusMinutes(1));
    UUID campaignId = dueCampaign.getPushCampaignId();

    List<UUID> dueCampaignIds = pushCampaignRepository.findDueCampaignIds(PushCampaignStatus.SCHEDULED, now, PageRequest.of(0, 20));
    Assertions.assertTrue(dueCampaignIds.contains(campaignId));
    Assertions.assertEquals(1, pushCampaignRepository.claimScheduledCampaign(campaignId, now));
    Assertions.assertEquals(0, pushCampaignRepository.claimScheduledCampaign(campaignId, now));
    Assertions.assertEquals(0, pushCampaignRepository.cancelScheduledCampaign(campaignId, now));

    pushCampaignRepository.addSendCounts(campaignId, 1000, 900, 100, 7);
    pushCampaignRepository.addSendCounts(campaignId, 10, 9, 1, 0);
    Assertions.assertEquals(1, pushCampaignRepository.finishSendingCampaign(campaignId, PushCampaignStatus.COMPLETED, null, now));
    Assertions.assertEquals(0, pushCampaignRepository.finishSendingCampaign(campaignId, PushCampaignStatus.FAILED, "x", now));

    UUID memberId = UUID.randomUUID();
    Assertions.assertEquals(1, pushCampaignOpenRepository.insertIfAbsent(UUID.randomUUID(), campaignId, memberId, now));
    Assertions.assertEquals(0, pushCampaignOpenRepository.insertIfAbsent(UUID.randomUUID(), campaignId, memberId, now));
    pushCampaignRepository.incrementOpenedCount(campaignId);

    PushCampaign finishedCampaign = pushCampaignRepository.findById(campaignId).orElseThrow();
    Assertions.assertEquals(PushCampaignStatus.COMPLETED, finishedCampaign.getStatus());
    Assertions.assertEquals(1010, finishedCampaign.getTargetMemberCount());
    Assertions.assertEquals(909, finishedCampaign.getSentCount());
    Assertions.assertEquals(101, finishedCampaign.getFailureCount());
    Assertions.assertEquals(7, finishedCampaign.getRemovedTokenCount());
    Assertions.assertEquals(1, finishedCampaign.getOpenedCount());

    PushCampaign futureCampaign = saveScheduledCampaign(now.plusHours(1));
    Assertions.assertEquals(1, pushCampaignRepository.cancelScheduledCampaign(futureCampaign.getPushCampaignId(), now));
    Assertions.assertEquals(0, pushCampaignRepository.claimScheduledCampaign(futureCampaign.getPushCampaignId(), now));

    PushCampaign staleCampaign = saveScheduledCampaign(now.minusHours(5));
    pushCampaignRepository.claimScheduledCampaign(staleCampaign.getPushCampaignId(), now.minusHours(4));
    Assertions.assertTrue(pushCampaignRepository.failStaleSendingCampaigns(now.minusHours(3), "stale", now) >= 1);
    pushCampaignRepository.claimScheduledCampaign(saveScheduledCampaign(now).getPushCampaignId(), now);
    Assertions.assertEquals(0, pushCampaignRepository.findDueCampaignIds(PushCampaignStatus.SCHEDULED, now.minusDays(1),
        PageRequest.of(0, 20)).stream().filter(campaignId::equals).count());
  }

  @Test
  @DisplayName("토큰 저장소 쿼리 실행 (조회·본인 토큰 삭제·일괄 삭제·오래된 토큰 삭제)")
  void fcmTokenQueries_실DB() {
    Assertions.assertTrue(fcmTokenRepository.findFirstByTokenOrderByUpdatedDateDesc("missing-token-874").isEmpty());
    Assertions.assertEquals(0, fcmTokenRepository.deleteByTokenAndMemberId("missing-token-874", UUID.randomUUID()));
    Assertions.assertEquals(0, fcmTokenRepository.deleteAllByTokenIn(List.of("missing-token-874")));
    Assertions.assertTrue(fcmTokenRepository.deleteAllByUpdatedDateBefore(LocalDateTime.now().minusYears(30)) >= 0);
  }
}
